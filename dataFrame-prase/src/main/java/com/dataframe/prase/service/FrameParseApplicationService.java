package com.dataframe.prase.service;

import com.dataframe.prase.infrastructure.csv.KingstVisCsvEdgeReader;
import com.dataframe.prase.common.ParserErrorCode;
import com.dataframe.prase.domain.dto.FrameParseRequest;
import com.dataframe.prase.domain.exception.ParserException;
import com.dataframe.prase.domain.model.DecodedFrame;
import com.dataframe.prase.domain.model.FrameParseResult;
import com.dataframe.prase.domain.model.SignalActivitySegment;
import com.dataframe.prase.domain.model.SignalEdge;
import com.dataframe.prase.domain.model.SignalLevelInterval;
import com.dataframe.prase.domain.model.UnrecognizedSignalRange;
import com.dataframe.prase.domain.protocol.ProtocolFrameDecoder;
import com.dataframe.prase.domain.protocol.DecodedFrameDeduplicator;
import com.dataframe.prase.infrastructure.excel.FrameParseExcelWriter;
import com.dataframe.prase.domain.signal.SignalActivitySegmenter;
import com.dataframe.prase.domain.signal.SignalIntervalBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public final class FrameParseApplicationService {

    private static final BigDecimal MILLISECONDS_PER_SECOND = new BigDecimal("1000");


    private final KingstVisCsvEdgeReader csvEdgeReader = new KingstVisCsvEdgeReader();
    private final SignalIntervalBuilder intervalBuilder = new SignalIntervalBuilder();
    private final SignalActivitySegmenter activitySegmenter = new SignalActivitySegmenter();
    private final DecodedFrameDeduplicator frameDeduplicator = new DecodedFrameDeduplicator();
    private final FrameParseExcelWriter excelResultWriter = new FrameParseExcelWriter();


    public FrameParseResult parse(FrameParseRequest options) {
        Objects.requireNonNull(options, "options");
        if (options.input().toAbsolutePath().normalize()
                .equals(options.output().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("输出文件不能与输入 CSV 相同");
        }

        List<SignalEdge> edges;
        try {
            edges = csvEdgeReader.read(options.input(), options.charset());
        } catch (IOException | RuntimeException exception) {
            throw new ParserException(ParserErrorCode.CSV_READ_FAILED, "CSV 文件读取失败", exception);
        }

        List<SignalActivitySegment> segments;
        List<DecodedFrame> frames;
        List<UnrecognizedSignalRange> boundaryFragments;
        try {
            var rules = options.configuration().protocolRuleSet();
            ProtocolFrameDecoder frameDecoder = new ProtocolFrameDecoder(rules);
            List<SignalLevelInterval> intervals = intervalBuilder.build(edges);
            BigDecimal idleThresholdSeconds = options.idleThresholdMs()
                    .divide(MILLISECONDS_PER_SECOND);
            segments = activitySegmenter
                    .segment(intervals, options.idleLevel(), idleThresholdSeconds)
                    .segments();
            List<DecodedFrame> frameCandidates = frameDecoder.decodeEstimated(
                    edges, options.remoteId(), options.levelMapping());
            frames = frameDeduplicator.deduplicateEstimated(
                    frameCandidates, rules.timing().dedupToleranceRatio());
            boundaryFragments = new ArrayList<>();
            for (SignalActivitySegment segment : segments) {
                List<DecodedFrame> relatedFrames = frames.stream()
                        .filter(frame -> isFrameInSegment(frame, segment))
                        .toList();
                boundaryFragments.addAll(uncoveredFragments(segment, relatedFrames));
            }
        } catch (ParserException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ParserException(ParserErrorCode.PROTOCOL_PARSE_FAILED, "协议解析失败", exception);
        }
        FrameParseResult outcome = new FrameParseResult(
                options.input(),
                options.output(),
                edges.size(),
                segments.size(),
                frames,
                boundaryFragments,
                options.configuration(),
                options.inputDisplayName(),
                LocalDateTime.now());
        try {
            excelResultWriter.write(outcome);
        } catch (IOException | RuntimeException exception) {
            throw new ParserException(ParserErrorCode.EXCEL_GENERATION_FAILED, "Excel 生成失败", exception);
        }
        return outcome;
    }

    private boolean isFrameInSegment(DecodedFrame frame, SignalActivitySegment segment) {
        BigDecimal preludeTime = frame.audit().preludeStartSeconds() == null
                ? frame.startSeconds()
                : frame.audit().preludeStartSeconds();
        return preludeTime.compareTo(segment.startSeconds()) >= 0
                && preludeTime.compareTo(segment.endSeconds()) < 0;
    }

    private List<UnrecognizedSignalRange> uncoveredFragments(
            SignalActivitySegment segment,
            List<DecodedFrame> relatedFrames) {
        List<DecodedFrame> orderedFrames = relatedFrames.stream()
                .sorted(Comparator.comparing(this::coverageStart))
                .toList();
        List<UnrecognizedSignalRange> fragments = new ArrayList<>();
        BigDecimal cursor = segment.startSeconds();
        for (DecodedFrame frame : orderedFrames) {
            BigDecimal frameStart = coverageStart(frame).max(segment.startSeconds());
            BigDecimal frameEnd = coverageEnd(frame).min(segment.endSeconds());
            if (cursor.compareTo(frameStart) < 0) {
                fragments.add(new UnrecognizedSignalRange(
                        cursor, frameStart, fragmentReason(segment, cursor, frameStart)));
            }
            if (frameEnd.compareTo(cursor) > 0) {
                cursor = frameEnd;
            }
        }
        if (cursor.compareTo(segment.endSeconds()) < 0) {
            fragments.add(new UnrecognizedSignalRange(
                    cursor,
                    segment.endSeconds(),
                    fragmentReason(segment, cursor, segment.endSeconds())));
        }
        return List.copyOf(fragments);
    }

    private BigDecimal coverageStart(DecodedFrame frame) {
        return frame.audit().preludeStartSeconds() == null
                ? frame.startSeconds()
                : frame.audit().preludeStartSeconds();
    }

    private BigDecimal coverageEnd(DecodedFrame frame) {
        return frame.audit().formalEndSeconds() == null
                ? frame.endSeconds()
                : frame.audit().formalEndSeconds();
    }

    private String fragmentReason(
            SignalActivitySegment segment,
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
