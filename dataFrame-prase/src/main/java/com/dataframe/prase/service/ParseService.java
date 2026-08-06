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

        List<FrameResult> frameCandidates = frameDecoder.decodeEstimated(
                edges, options.remoteId(), options.levelMapping());
        List<FrameResult> frames = frameDeduplicator.deduplicateEstimated(frameCandidates);
        List<BoundaryFragment> boundaryFragments = new ArrayList<>();
        for (ActivitySegment segment : segments) {
            List<FrameResult> relatedFrames = frames.stream()
                    .filter(frame -> isFrameInSegment(frame, segment))
                    .toList();
            boundaryFragments.addAll(uncoveredFragments(segment, relatedFrames));
        }
        ParseOutcome outcome = new ParseOutcome(
                options.input(),
                options.output(),
                edges.size(),
                segments.size(),
                frames,
                boundaryFragments,
                options.remoteId(),
                options.idleThresholdMs(),
                options.idleLevel(),
                options.levelMapping(),
                LocalDateTime.now());
        excelResultWriter.write(outcome);
        return outcome;
    }

    private boolean isFrameInSegment(FrameResult frame, ActivitySegment segment) {
        BigDecimal preludeTime = frame.audit().preludeStartSeconds() == null
                ? frame.startSeconds()
                : frame.audit().preludeStartSeconds();
        return preludeTime.compareTo(segment.startSeconds()) >= 0
                && preludeTime.compareTo(segment.endSeconds()) < 0;
    }

    private List<BoundaryFragment> uncoveredFragments(
            ActivitySegment segment,
            List<FrameResult> relatedFrames) {
        List<FrameResult> orderedFrames = relatedFrames.stream()
                .sorted(Comparator.comparing(this::coverageStart))
                .toList();
        List<BoundaryFragment> fragments = new ArrayList<>();
        BigDecimal cursor = segment.startSeconds();
        for (FrameResult frame : orderedFrames) {
            BigDecimal frameStart = coverageStart(frame).max(segment.startSeconds());
            BigDecimal frameEnd = coverageEnd(frame).min(segment.endSeconds());
            if (cursor.compareTo(frameStart) < 0) {
                fragments.add(new BoundaryFragment(
                        cursor, frameStart, fragmentReason(segment, cursor, frameStart)));
            }
            if (frameEnd.compareTo(cursor) > 0) {
                cursor = frameEnd;
            }
        }
        if (cursor.compareTo(segment.endSeconds()) < 0) {
            fragments.add(new BoundaryFragment(
                    cursor,
                    segment.endSeconds(),
                    fragmentReason(segment, cursor, segment.endSeconds())));
        }
        return List.copyOf(fragments);
    }

    private BigDecimal coverageStart(FrameResult frame) {
        return frame.audit().preludeStartSeconds() == null
                ? frame.startSeconds()
                : frame.audit().preludeStartSeconds();
    }

    private BigDecimal coverageEnd(FrameResult frame) {
        return frame.audit().formalEndSeconds() == null
                ? frame.endSeconds()
                : frame.audit().formalEndSeconds();
    }

    private String fragmentReason(
            ActivitySegment segment,
            BigDecimal startSeconds,
            BigDecimal endSeconds) {
        if (!segment.leftBoundaryKnown()
                && startSeconds.compareTo(segment.startSeconds()) == 0) {
            return "文件开头未识别数据";
        }
        if (!segment.rightBoundaryKnown()
                && endSeconds.compareTo(segment.endSeconds()) == 0) {
            return "文件结尾未识别数据";
        }
        return "未识别数据范围";
    }
}
