package com.hex.hex_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ColorMathService {

    private static final double MAX_DISTANCE = 441.6729559300637;

    public BigDecimal calculateMatchScore(int[] targetRgb, int[] photoRgb) {
        if (targetRgb.length != 3 || photoRgb.length != 3) {
            throw new IllegalArgumentException("RGB arrays must have exactly 3 elements.");
        }

        double distance = Math.sqrt(
            Math.pow(targetRgb[0] - photoRgb[0], 2) +
            Math.pow(targetRgb[1] - photoRgb[1], 2) +
            Math.pow(targetRgb[2] - photoRgb[2], 2)
        );

        double rawScore = (1 - (distance / MAX_DISTANCE)) * 100;
        double finalScore = rawScore;

        if (rawScore >= 88.0) {
            double smoothedScore = rawScore + ((100.0 - rawScore) * 0.5);
            finalScore = Math.min(100.0, smoothedScore);
        } else {
            finalScore = Math.max(0.0, rawScore);
        }

        return BigDecimal.valueOf(finalScore).setScale(2, RoundingMode.HALF_UP);
    }
    
    public int[] hexToRgb(String hex) {
        String cleanHex = hex.replace("#", "");
        if (!cleanHex.matches("^[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid Hex color format.");
        }
        return new int[]{
            Integer.valueOf(cleanHex.substring(0, 2), 16),
            Integer.valueOf(cleanHex.substring(2, 4), 16),
            Integer.valueOf(cleanHex.substring(4, 6), 16)
        };
    }
}
