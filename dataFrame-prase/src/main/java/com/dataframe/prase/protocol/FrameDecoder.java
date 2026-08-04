package com.dataframe.prase.protocol;

import com.dataframe.prase.model.ActivitySegment;
import com.dataframe.prase.model.BoundaryFragment;
import com.dataframe.prase.model.FrameResult;
import com.dataframe.prase.signal.FixedPeriodSampler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FrameDecoder {

    private static final int CORE_FRAME_BITS = 112;
    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");
    private static final List<Integer> MARKER_BITS = List.of(
            1, 0, 1, 0, 1, 0, 1, 0,
            0, 0, 1, 0, 1, 1, 0, 1,
            1, 1, 0, 1, 0, 1, 0, 0);

    private final FrameRules frameRules;

    public FrameDecoder(FrameRules frameRules) {
        this.frameRules = Objects.requireNonNull(frameRules, "frameRules");
    }

    public List<FrameResult> decode(
            ActivitySegment segment,
            FixedPeriodSampler.SampledBits sampled,
            List<Integer> expectedRemoteId) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(sampled, "sampled");
        List<FrameResult> results = new ArrayList<>();
        int searchFrom = 0;

        while (searchFrom <= sampled.bits().size() - MARKER_BITS.size()) {
            int startBit = findMarker(sampled.bits(), searchFrom);
            if (startBit < 0) {
                break;
            }
            int availableBits = Math.min(CORE_FRAME_BITS, sampled.bits().size() - startBit);
            List<Integer> coreBits = List.copyOf(sampled.bits().subList(startBit, startBit + availableBits));
            List<Integer> recoveredBytes = toBytes(coreBits);
            FrameRules.ValidationResult validation = frameRules.validate(recoveredBytes, expectedRemoteId);
            BigDecimal startSeconds = sampled.sampleTimesSeconds().get(startBit);
            BigDecimal endSeconds = frameEnd(
                    startSeconds, availableBits, sampled.bitPeriodUs(), segment.endSeconds());

            results.add(new FrameResult(
                    0,
                    segment.number(),
                    true,
                    validation.complete(),
                    validation.valid(),
                    startSeconds,
                    endSeconds,
                    sampled.bitPeriodUs(),
                    sampled.phaseUs(),
                    startBit,
                    toBitString(coreBits),
                    recoveredBytes,
                    validation.failureReasons(),
                    validation.commandInfo().channel(),
                    validation.commandInfo().action()));

            searchFrom = validation.complete() ? startBit + CORE_FRAME_BITS : sampled.bits().size();
        }
        return List.copyOf(results);
    }

    public FrameResult unmatched(ActivitySegment segment, FixedPeriodSampler.SampledBits sampled) {
        List<Integer> bytes = toBytes(sampled.bits());
        BigDecimal start = sampled.sampleTimesSeconds().isEmpty()
                ? segment.startSeconds()
                : sampled.sampleTimesSeconds().get(0);
        return new FrameResult(
                0,
                segment.number(),
                false,
                false,
                false,
                start,
                segment.endSeconds(),
                sampled.bitPeriodUs(),
                sampled.phaseUs(),
                -1,
                sampled.bitString(),
                bytes,
                List.of("未找到识别开头"),
                "未知通道",
                "未知命令");
    }

    public List<BoundaryFragment> boundaryFragments(
            ActivitySegment segment,
            List<FrameResult> frames) {
        if (!segment.isFileBoundarySegment()) {
            return List.of();
        }
        List<FrameResult> ordered = frames.stream()
                .sorted(Comparator.comparing(FrameResult::startSeconds))
                .toList();
        if (ordered.isEmpty()) {
            return List.of(new BoundaryFragment(
                    segment.startSeconds(),
                    segment.endSeconds(),
                    "文件边界缺少完整活动边界且未找到识别开头"));
        }

        List<BoundaryFragment> fragments = new ArrayList<>();
        FrameResult first = ordered.get(0);
        if (!segment.leftBoundaryKnown()
                && segment.startSeconds().compareTo(first.startSeconds()) < 0) {
            fragments.add(new BoundaryFragment(
                    segment.startSeconds(), first.startSeconds(), "文件开头残片"));
        }
        FrameResult last = ordered.get(ordered.size() - 1);
        if (!segment.rightBoundaryKnown()
                && last.endSeconds().compareTo(segment.endSeconds()) < 0) {
            fragments.add(new BoundaryFragment(
                    last.endSeconds(), segment.endSeconds(), "文件结尾残片"));
        }
        return List.copyOf(fragments);
    }

    private int findMarker(List<Integer> bits, int fromIndex) {
        for (int index = fromIndex; index <= bits.size() - MARKER_BITS.size(); index++) {
            boolean matches = true;
            for (int markerIndex = 0; markerIndex < MARKER_BITS.size(); markerIndex++) {
                if (!bits.get(index + markerIndex).equals(MARKER_BITS.get(markerIndex))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        return -1;
    }

    private List<Integer> toBytes(List<Integer> bits) {
        List<Integer> bytes = new ArrayList<>();
        for (int offset = 0; offset + 7 < bits.size(); offset += 8) {
            int value = 0;
            for (int bit = 0; bit < 8; bit++) {
                value = (value << 1) | bits.get(offset + bit);
            }
            bytes.add(value);
        }
        return List.copyOf(bytes);
    }

    private String toBitString(List<Integer> bits) {
        StringBuilder builder = new StringBuilder(bits.size());
        for (int bit : bits) {
            builder.append(bit);
        }
        return builder.toString();
    }

    private BigDecimal frameEnd(
            BigDecimal startSeconds,
            int bitCount,
            BigDecimal bitPeriodUs,
            BigDecimal segmentEnd) {
        BigDecimal duration = bitPeriodUs
                .multiply(BigDecimal.valueOf(bitCount))
                .divide(MICROSECONDS_PER_SECOND);
        BigDecimal calculatedEnd = startSeconds.add(duration);
        return calculatedEnd.min(segmentEnd);
    }
}
