package com.dataframe.prase.service;

import com.dataframe.prase.cli.CliOptions;
import com.dataframe.prase.csv.CsvEdgeReader;
import com.dataframe.prase.model.ActivitySegment;
import com.dataframe.prase.model.BoundaryFragment;
import com.dataframe.prase.model.EdgeRecord;
import com.dataframe.prase.model.FrameResult;
import com.dataframe.prase.model.LevelInterval;
import com.dataframe.prase.model.ParseOutcome;
import com.dataframe.prase.protocol.FrameDecoder;
import com.dataframe.prase.protocol.FrameDeduplicator;
import com.dataframe.prase.protocol.FrameRules;
import com.dataframe.prase.report.ExcelResultWriter;
import com.dataframe.prase.signal.ActivitySegmenter;
import com.dataframe.prase.signal.FixedPeriodSampler;
import com.dataframe.prase.signal.IntervalBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ParseService {

    private static final BigDecimal MILLISECONDS_PER_SECOND = new BigDecimal("1000");

    private final CsvEdgeReader csvEdgeReader = new CsvEdgeReader();
    private final IntervalBuilder intervalBuilder = new IntervalBuilder();
    private final ActivitySegmenter activitySegmenter = new ActivitySegmenter();
    private final FixedPeriodSampler fixedPeriodSampler = new FixedPeriodSampler();
    private final FrameDecoder frameDecoder = new FrameDecoder(new FrameRules());
    private final FrameDeduplicator frameDeduplicator = new FrameDeduplicator();
    private final ExcelResultWriter excelResultWriter = new ExcelResultWriter();

    public ParseOutcome parse(CliOptions options) throws IOException {
        Objects.requireNonNull(options, "options");
        if (options.help()) {
            throw new IllegalArgumentException("帮助参数不能执行 CSV 解析");
        }
        if (options.input().toAbsolutePath().normalize()
                .equals(options.output().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("输出文件不能与输入 CSV 相同");
        }

        List<EdgeRecord> edges = csvEdgeReader.read(options.input(), options.charset());
        List<LevelInterval> intervals = intervalBuilder.build(edges);
        BigDecimal idleThresholdSeconds = options.idleThresholdMs()
                .divide(MILLISECONDS_PER_SECOND);
        List<ActivitySegment> segments = activitySegmenter
                .segment(intervals, options.idleLevel(), idleThresholdSeconds)
                .segments();

        List<FrameResult> frameCandidates = new ArrayList<>();
        List<BoundaryFragment> boundaryFragments = new ArrayList<>();
        for (ActivitySegment segment : segments) {
            SegmentDecodeResult result = decodeSegment(segment, options);
            frameCandidates.addAll(result.frames());
            boundaryFragments.addAll(result.boundaryFragments());
        }

        List<FrameResult> frames = frameDeduplicator.deduplicate(
                frameCandidates, options.dedupToleranceUs());
        ParseOutcome outcome = new ParseOutcome(
                options.input(),
                options.output(),
                edges.size(),
                segments.size(),
                frames,
                boundaryFragments,
                options.remoteId(),
                options.bitPeriodUs(),
                options.phaseStepUs(),
                options.idleThresholdMs(),
                options.idleLevel(),
                options.levelMapping(),
                LocalDateTime.now());
        excelResultWriter.write(outcome);
        return outcome;
    }

    private SegmentDecodeResult decodeSegment(ActivitySegment segment, CliOptions options) {
        List<FixedPeriodSampler.SampledBits> sampledPhases = fixedPeriodSampler.sample(
                segment,
                options.bitPeriodUs(),
                options.phaseStepUs(),
                options.levelMapping());
        List<FrameResult> candidates = new ArrayList<>();
        for (FixedPeriodSampler.SampledBits sampled : sampledPhases) {
            candidates.addAll(frameDecoder.decode(segment, sampled, options.remoteId()));
        }

        List<FrameResult> frames = frameDeduplicator.deduplicate(
                candidates, options.dedupToleranceUs());
        if (frames.isEmpty() && !segment.isFileBoundarySegment()) {
            frames = List.of(frameDecoder.unmatched(segment, bestAuditSample(sampledPhases)));
        }
        List<BoundaryFragment> fragments = frameDecoder.boundaryFragments(segment, frames);
        return new SegmentDecodeResult(frames, fragments);
    }

    private FixedPeriodSampler.SampledBits bestAuditSample(
            List<FixedPeriodSampler.SampledBits> sampledPhases) {
        return sampledPhases.stream()
                .max(Comparator.comparingInt((FixedPeriodSampler.SampledBits sample) -> sample.bits().size())
                        .thenComparing(FixedPeriodSampler.SampledBits::phaseUs, Comparator.reverseOrder()))
                .orElseThrow(() -> new IllegalStateException("候选区段没有可用采样相位"));
    }

    private record SegmentDecodeResult(
            List<FrameResult> frames,
            List<BoundaryFragment> boundaryFragments) {

        private SegmentDecodeResult {
            frames = List.copyOf(frames);
            boundaryFragments = List.copyOf(boundaryFragments);
        }
    }
}
