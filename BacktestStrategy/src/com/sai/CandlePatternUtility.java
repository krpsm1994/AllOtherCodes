package com.sai;

import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to detect common candlestick patterns on 15-min candles.
 *
 * Single-candle patterns (9:15 AM candle on target day):
 *  - Marubozu         : bullish body, both wicks <= 5% of range
 *  - Doji             : body <= 10% of range (indecision)
 *  - DragonflDoji     : open ≈ close ≈ high, long lower wick
 *  - VeryBullish      : large lower wick, tiny upper wick (bullish pin bar)
 *  - Hammer           : small body at top, lower wick >= 2x body, tiny upper wick
 *  - BullishBeltHold  : opens at/near low (no lower wick), closes near high
 *  - SpinningTop      : small body centered, roughly equal wicks
 *
 * Two-candle patterns (9:15 AM vs previous 15-min candle):
 *  - BullEngulf       : bullish body fully engulfs previous bearish body
 *  - BullKicker       : gap-up from bearish close to bullish open
 *  - PiercingLine     : opens below prev low, closes above prev midpoint
 *  - TweezerBottom    : previous bearish + current bullish sharing same low
 *  - BullHarami       : small bullish body fully inside previous large bearish body
 *
 * Multi-candle patterns (last candles of previous trading day):
 *  - MorningStar          : large bearish → small star → large bullish (3-candle)
 *  - ThreeWhiteSoldiers   : 3 consecutive bullish candles, each closing higher (3-candle)
 *  - RisingThreeMethods   : large bull → 3 small pullbacks → large bull breakout (5-candle)
 *  - RisingWindow         : gap-up between previous close and current open (2-candle)
 *  - BullishMatHold       : large bull → small pullback staying above prev midpoint (2-candle)
 *  - UpsideTasukiGap      : 2 bull candles with gap, 3rd bearish partially fills but doesn't close gap (3-candle)
 *  - StrongBullish        : large body candle >= 60% of range (single, continuation)
 *  - ShootingStar         : big upper wick at top of uptrend — bearish reversal signal
 *  - InvertedHammer       : big upper wick at bottom — potential bullish reversal
 */
public class CandlePatternUtility {

    // --- Threshold constants (as fraction of candle range) ---
    private static final double DOJI_BODY_PCT            = 0.10; // body <= 10% of range
    private static final double MARUBOZU_WICK_PCT        = 0.05; // each wick <= 5% of range
    private static final double BIG_LOWER_WICK_PCT       = 0.55; // lower wick >= 55% of range
    private static final double SMALL_UPPER_WICK_PCT     = 0.15; // upper wick <= 15% of range
    private static final double MORNING_STAR_BODY_PCT    = 0.40; // "large" body >= 40% of range
    private static final double HAMMER_WICK_RATIO        = 2.0;  // lower wick >= 2x body size
    private static final double HAMMER_UPPER_WICK_PCT    = 0.10; // upper wick <= 10% of range
    private static final double BELT_HOLD_LOWER_WICK_PCT = 0.05; // lower wick <= 5% of range (opens near low)
    private static final double BELT_HOLD_UPPER_WICK_PCT = 0.10; // upper wick <= 10% of range (closes near high)
    private static final double SPINNING_TOP_BODY_PCT    = 0.30; // body <= 30% of range
    private static final double SPINNING_TOP_WICK_RATIO  = 0.60; // each wick >= 60% of half-remainder
    private static final double LARGE_BODY_PCT           = 0.50; // "large" body >= 50% of range (soldiers/methods)
    private static final double STRONG_BODY_PCT          = 0.60; // strong continuation body >= 60% of range
    private static final double TWEEZER_TOLERANCE_PCT    = 0.001;// lows within 0.1% of each other
    private static final double SHOOTING_STAR_UPPER_PCT  = 0.55; // upper wick >= 55% of range
    private static final double SHOOTING_STAR_BODY_PCT   = 0.25; // body <= 25% of range
    private static final double SHOOTING_STAR_LOWER_PCT  = 0.15; // lower wick <= 15% of range

    public static String detectBerishPatterns(List<HistoricalDataFetcher.Candle> candles15m,
                LocalDate targetDay) {
            if (candles15m == null || candles15m.isEmpty()) return "No15mData";

            // Find index of the first 15-min candle on targetDay (9:15 AM candle)
            int targetIdx = -1;
            for (int i = 0; i < candles15m.size(); i++) {
            	if(candles15m.get(i).timestamp.isEmpty()) {
            		break;
            	}
                LocalDate d = ZonedDateTime.parse(candles15m.get(i).timestamp).toLocalDate();
                if (d.equals(targetDay)) {
                    targetIdx = i;
                    break;
                }
            }

            if (targetIdx < 0) return "No15mData";

            HistoricalDataFetcher.Candle current  = candles15m.get(targetIdx);
            HistoricalDataFetcher.Candle previous = targetIdx > 0 ? candles15m.get(targetIdx - 1) : null;

            List<String> patterns = new ArrayList<>();

            // ---- Single-candle patterns on current (9:15 AM) candle ----
            if (isBearishMarubozu(current)) {
                patterns.add("BearMarubozu");
            }
            if (isGravestoneDoji(current)) {
                patterns.add("GravestoneDoji");
            } else if (isDoji(current)) {
                patterns.add("Doji");
            }
            if (isVeryBearishCandle(current)) {
                patterns.add("VeryBearish");
            }
            if (isShootingStar(current)) {
                patterns.add("ShootingStar");
            }
            if (isBearishBeltHold(current)) {
                patterns.add("BearBeltHold");
            }
            if (isSpinningTop(current)) {
                patterns.add("SpinningTop");
            }
            if (isStrongBearish(current)) {
                patterns.add("StrongBearish");
            }
            if (isHammer(current)) {
                patterns.add("Hammer");
            }
            if (isInvertedHammer(current)) {
                patterns.add("InvertedHammer");
            }
            if (isDragonflyDoji(current)) {
                patterns.add("DragonflyDoji");
            }
            if (isLongLeggedDoji(current)) {
                patterns.add("LongLeggedDoji");
            }

            // ---- Two-candle patterns (current vs previous 15-min candle) ----
            if (previous != null) {
                if (isBearishEngulfing(current, previous)) {
                    patterns.add("BearEngulf");
                }
                if (isBearishKicker(current, previous)) {
                    patterns.add("BearKicker");
                }
                if (isDarkCloudCover(current, previous)) {
                    patterns.add("DarkCloudCover");
                }
                if (isTweezerTop(current, previous)) {
                    patterns.add("TweezerTop");
                }
                if (isBearishHarami(current, previous)) {
                    patterns.add("BearHarami");
                }
                if (isBearishHaramiCross(current, previous)) {
                    patterns.add("BearHaramiCross");
                }
                if (isSeparatingLinesBearish(current, previous)) {
                    patterns.add("SeparatingLinesBear");
                }
                if (isBearishCounterAttack(current, previous)) {
                    patterns.add("BearCounterAttack");
                }
            }

            // ---- Multi-candle patterns: prev day candles + current (9:15 AM) as last candle ----
            List<HistoricalDataFetcher.Candle> prevDayCandles = getPreviousDayCandles(candles15m, targetDay);
            int sz = prevDayCandles.size();
            // 2-candle: last candle of prev day (c1) + current 9:15 AM candle (c2)
            if (sz >= 1) {
                if (isFallingWindow(prevDayCandles.get(sz - 1), current)) {
                    patterns.add("FallingWindow");
                }
                if (isBearishMatHold(prevDayCandles.get(sz - 1), current)) {
                    patterns.add("BearMatHold");
                }
            }
            // 3-candle: last 2 prev day candles (c1,c2) + current 9:15 AM candle (c3)
            if (sz >= 2) {
                if (isEveningStar(prevDayCandles.get(sz - 2),
                                  prevDayCandles.get(sz - 1),
                                  current)) {
                    patterns.add("EveningStar");
                }
                if (isThreeBlackCrows(prevDayCandles.get(sz - 2),
                                       prevDayCandles.get(sz - 1),
                                       current)) {
                    patterns.add("ThreeBlackCrows");
                }
                if (isDownsideTasukiGap(prevDayCandles.get(sz - 2),
                                         prevDayCandles.get(sz - 1),
                                         current)) {
                    patterns.add("DownsideTasukiGap");
                }
                if (isThreeInsideDown(prevDayCandles.get(sz - 2),
                                       prevDayCandles.get(sz - 1),
                                       current)) {
                    patterns.add("ThreeInsideDown");
                }
                if (isThreeOutsideDown(prevDayCandles.get(sz - 2),
                                        prevDayCandles.get(sz - 1),
                                        current)) {
                    patterns.add("ThreeOutsideDown");
                }
                if (isAbandonedBabyBear(prevDayCandles.get(sz - 2),
                                         prevDayCandles.get(sz - 1),
                                         current)) {
                    patterns.add("AbandonedBabyBear");
                }
            }
            // 4-candle: last 3 prev day candles (c1-c3) + current 9:15 AM candle (c4)
            if (sz >= 3) {
                if (isThreeLineStrikeBear(prevDayCandles.get(sz - 3),
                                           prevDayCandles.get(sz - 2),
                                           prevDayCandles.get(sz - 1),
                                           current)) {
                    patterns.add("ThreeLineStrikeBear");
                }
            }
            // 5-candle: last 4 prev day candles (c1-c4) + current 9:15 AM candle (c5)
            if (sz >= 4) {
                if (isFallingThreeMethods(prevDayCandles.get(sz - 4),
                                           prevDayCandles.get(sz - 3),
                                           prevDayCandles.get(sz - 2),
                                           prevDayCandles.get(sz - 1),
                                           current)) {
                    patterns.add("FallingThreeMethods");
                }
            }

            return patterns.isEmpty() ? "-" : String.join(",", patterns);
        }

    /**
     * Detect patterns for the opening (9:15 AM) 15-min candle on targetDay.
     * Returns a comma-separated string of matched pattern names, or "-" if none.
     */
    public static String detectOpeningCandlePatterns(
            List<HistoricalDataFetcher.Candle> candles15m,
            LocalDate targetDay) {

        if (candles15m == null || candles15m.isEmpty()) return "No15mData";

        // Find index of the first 15-min candle on targetDay (9:15 AM candle)
        int targetIdx = -1;
        for (int i = 0; i < candles15m.size(); i++) {
        	if(candles15m.get(i).timestamp.isEmpty()) {
        		break;
        	}
            LocalDate d = ZonedDateTime.parse(candles15m.get(i).timestamp).toLocalDate();
            if (d.equals(targetDay)) {
                targetIdx = i;
                break;
            }
        }

        if (targetIdx < 0) return "No15mData";

        HistoricalDataFetcher.Candle current  = candles15m.get(targetIdx);
        HistoricalDataFetcher.Candle previous = targetIdx > 0 ? candles15m.get(targetIdx - 1) : null;

        List<String> patterns = new ArrayList<>();

        // ---- Single-candle patterns on current (9:15 AM) candle ----
        if (isBullishMarubozu(current)) {
            patterns.add("Marubozu");
        }
        if (isDragonflyDoji(current)) {
            patterns.add("DragonflDoji");
        } else if (isDoji(current)) {
            patterns.add("Doji");
        }
        if (isVeryBullishCandle(current)) {
            patterns.add("VeryBullish");
        }
        if (isHammer(current)) {
            patterns.add("Hammer");
        }
        if (isBullishBeltHold(current)) {
            patterns.add("BullBeltHold");
        }
        if (isSpinningTop(current)) {
            patterns.add("SpinningTop");
        }
        if (isStrongBullish(current)) {
            patterns.add("StrongBullish");
        }
        if (isShootingStar(current)) {
            patterns.add("ShootingStar");
        }
        if (isInvertedHammer(current)) {
            patterns.add("InvertedHammer");
        }
        if (isGravestoneDoji(current)) {
            patterns.add("GravestoneDoji");
        }
        if (isLongLeggedDoji(current)) {
            patterns.add("LongLeggedDoji");
        }

        // ---- Two-candle patterns (current vs previous 15-min candle) ----
        if (previous != null) {
            if (isBullishEngulfing(current, previous)) {
                patterns.add("BullEngulf");
            }
            if (isBullishKicker(current, previous)) {
                patterns.add("BullKicker");
            }
            if (isPiercingLine(current, previous)) {
                patterns.add("PiercingLine");
            }
            if (isTweezerBottom(current, previous)) {
                patterns.add("TweezerBottom");
            }
            if (isBullishHarami(current, previous)) {
                patterns.add("BullHarami");
            }
            if (isBullishHaramiCross(current, previous)) {
                patterns.add("BullHaramiCross");
            }
            if (isSeparatingLines(current, previous)) {
                patterns.add("SeparatingLines");
            }
            if (isBullishCounterAttack(current, previous)) {
                patterns.add("BullCounterAttack");
            }
        }

        // ---- Multi-candle patterns: prev day candles + current (9:15 AM) as last candle ----
        List<HistoricalDataFetcher.Candle> prevDayCandles = getPreviousDayCandles(candles15m, targetDay);
        int sz = prevDayCandles.size();
        // 2-candle: last candle of prev day (c1) + current 9:15 AM candle (c2)
        if (sz >= 1) {
            if (isRisingWindow(prevDayCandles.get(sz - 1), current)) {
                patterns.add("RisingWindow");
            }
            if (isBullishMatHold(prevDayCandles.get(sz - 1), current)) {
                patterns.add("BullMatHold");
            }
        }
        // 3-candle: last 2 prev day candles (c1,c2) + current 9:15 AM candle (c3)
        if (sz >= 2) {
            if (isMorningStar(prevDayCandles.get(sz - 2),
                              prevDayCandles.get(sz - 1),
                              current)) {
                patterns.add("MorningStar");
            }
            if (isThreeWhiteSoldiers(prevDayCandles.get(sz - 2),
                                     prevDayCandles.get(sz - 1),
                                     current)) {
                patterns.add("ThreeWhiteSoldiers");
            }
            if (isUpsideTasukiGap(prevDayCandles.get(sz - 2),
                                   prevDayCandles.get(sz - 1),
                                   current)) {
                patterns.add("UpsideTasukiGap");
            }
            if (isThreeInsideUp(prevDayCandles.get(sz - 2),
                                 prevDayCandles.get(sz - 1),
                                 current)) {
                patterns.add("ThreeInsideUp");
            }
            if (isThreeOutsideUp(prevDayCandles.get(sz - 2),
                                  prevDayCandles.get(sz - 1),
                                  current)) {
                patterns.add("ThreeOutsideUp");
            }
            if (isAbandonedBaby(prevDayCandles.get(sz - 2),
                                 prevDayCandles.get(sz - 1),
                                 current)) {
                patterns.add("AbandonedBaby");
            }
        }
        // 4-candle: last 3 prev day candles (c1-c3) + current 9:15 AM candle (c4)
        if (sz >= 3) {
            if (isThreeLineStrike(prevDayCandles.get(sz - 3),
                                   prevDayCandles.get(sz - 2),
                                   prevDayCandles.get(sz - 1),
                                   current)) {
                patterns.add("ThreeLineStrike");
            }
        }
        // 5-candle: last 4 prev day candles (c1-c4) + current 9:15 AM candle (c5)
        if (sz >= 4) {
            if (isRisingThreeMethods(prevDayCandles.get(sz - 4),
                                     prevDayCandles.get(sz - 3),
                                     prevDayCandles.get(sz - 2),
                                     prevDayCandles.get(sz - 1),
                                     current)) {
                patterns.add("RisingThreeMethods");
            }
        }

        return patterns.isEmpty() ? "-" : String.join(",", patterns);
    }

    // -----------------------------------------------------------------------
    // Pattern detection helpers
    // -----------------------------------------------------------------------

    /**
     * Bullish Marubozu: close > open, both upper and lower wicks <= 5% of range.
     */
    public static boolean isBullishMarubozu(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close <= c.open) return false;          // must be bullish
        double upperWick = c.high - c.close;
        double lowerWick = c.open - c.low;
        return upperWick <= MARUBOZU_WICK_PCT * range
            && lowerWick <= MARUBOZU_WICK_PCT * range;
    }

    /**
     * Doji: |close - open| <= 10% of (high - low).
     */
    public static boolean isDoji(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        return Math.abs(c.close - c.open) <= DOJI_BODY_PCT * range;
    }

    /**
     * Very bullish candle (hammer / bullish pin bar):
     *   - Bullish body (close > open)
     *   - Lower wick >= 55% of total range
     *   - Upper wick <= 15% of total range
     */
    public static boolean isVeryBullishCandle(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close <= c.open) return false;           // bullish body required
        double lowerWick = c.open - c.low;             // open is bottom of bullish body
        double upperWick = c.high - c.close;
        return lowerWick >= BIG_LOWER_WICK_PCT * range
            && upperWick <= SMALL_UPPER_WICK_PCT * range;
    }

    /**
     * Bullish engulfing:
     *   - Previous candle is bearish (close < open)
     *   - Current candle is bullish (close > open)
     *   - Current bullish body fully contains previous bearish body
     */
    public static boolean isBullishEngulfing(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current must be bullish
        if (previous.close >= previous.open) return false; // previous must be bearish
        // current body (open..close) engulfs previous body (close..open)
        return current.open  <= previous.close
            && current.close >= previous.open;
    }

    /**
     * Dragonfly Doji: open ≈ close ≈ high, long lower wick.
     * Upper wick <= 5% of range, body <= 10% of range.
     */
    public static boolean isDragonflyDoji(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double upperWick = c.high - Math.max(c.open, c.close);
        return body      <= DOJI_BODY_PCT * range
            && upperWick <= MARUBOZU_WICK_PCT * range;
    }

    /**
     * Hammer: small body near top of range, lower wick >= 2× body, tiny upper wick.
     * Body direction (bull/bear) doesn't matter — context provides bullish signal.
     */
    public static boolean isHammer(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double lowerWick = Math.min(c.open, c.close) - c.low;
        double upperWick = c.high - Math.max(c.open, c.close);
        if (body <= 0) return false;
        return lowerWick >= HAMMER_WICK_RATIO * body
            && upperWick <= HAMMER_UPPER_WICK_PCT * range;
    }

    /**
     * Bullish Belt Hold: opens at/near the low (no lower wick), bullish close near high.
     * Lower wick <= 5% of range, upper wick <= 10% of range, bullish body.
     */
    public static boolean isBullishBeltHold(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close <= c.open) return false;           // must be bullish
        double lowerWick = c.open - c.low;
        double upperWick = c.high - c.close;
        return lowerWick <= BELT_HOLD_LOWER_WICK_PCT * range
            && upperWick <= BELT_HOLD_UPPER_WICK_PCT * range;
    }

    /**
     * Spinning Top: small body (<=30% of range) centered, each wick roughly equal
     * and each >= 30% of range. Signals indecision but can precede a bullish move.
     */
    public static boolean isSpinningTop(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double upperWick = c.high - Math.max(c.open, c.close);
        double lowerWick = Math.min(c.open, c.close) - c.low;
        if (body > SPINNING_TOP_BODY_PCT * range) return false;
        // Both wicks should be present and roughly equal (within 60% of each other)
        if (upperWick < 0.20 * range || lowerWick < 0.20 * range) return false;
        double larger  = Math.max(upperWick, lowerWick);
        double smaller = Math.min(upperWick, lowerWick);
        return smaller >= SPINNING_TOP_WICK_RATIO * larger;
    }

    /**
     * Bullish Kicker: previous candle is bearish, current candle opens above
     * the previous candle's open (gap-up) and is bullish.
     */
    public static boolean isBullishKicker(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current bullish
        if (previous.close >= previous.open) return false; // previous bearish
        return current.open > previous.open;               // gap-up
    }

    /**
     * Piercing Line:
     *   - Previous candle is bearish
     *   - Current opens below previous low
     *   - Current closes above the midpoint of the previous bearish body
     *   - Current is bullish
     */
    public static boolean isPiercingLine(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current bullish
        if (previous.close >= previous.open) return false; // previous bearish
        double prevMid = (previous.open + previous.close) / 2.0;
        return current.open  < previous.low
            && current.close > prevMid
            && current.close < previous.open;  // hasn't fully engulfed
    }

    /**
     * Tweezer Bottom: two candles (first bearish, second bullish) sharing the same low.
     * Lows are within 0.1% of each other.
     */
    public static boolean isTweezerBottom(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current bullish
        if (previous.close >= previous.open) return false; // previous bearish
        double avgLow = (previous.low + current.low) / 2.0;
        if (avgLow <= 0) return false;
        return Math.abs(current.low - previous.low) / avgLow <= TWEEZER_TOLERANCE_PCT;
    }

    /**
     * Bullish Harami:
     *   - Previous candle is a large bearish candle
     *   - Current small bullish body is fully contained within the previous body
     */
    public static boolean isBullishHarami(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current bullish
        if (previous.close >= previous.open) return false; // previous bearish
        // current body fully inside previous body
        return current.open  >= previous.close
            && current.close <= previous.open;
    }

    /**
     * Morning star (3-candle bullish reversal):
     *   c1 – large bearish candle    (body >= 40% of range)
     *   c2 – small-body star candle  (doji or body <= 30% of range)
     *   c3 – large bullish candle    (body >= 40% of range, closes above midpoint of c1)
     */
    public static boolean isMorningStar(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        double range1 = c1.high - c1.low;
        double range2 = c2.high - c2.low;
        double range3 = c3.high - c3.low;
        if (range1 <= 0 || range3 <= 0) return false;

        // c1: large bearish
        double body1 = c1.open - c1.close;
        if (body1 < MORNING_STAR_BODY_PCT * range1) return false;

        // c2: small body (star)
        double starBodyRatio = range2 > 0 ? Math.abs(c2.close - c2.open) / range2 : 1.0;
        if (starBodyRatio > 0.30) return false;

        // c3: large bullish
        double body3 = c3.close - c3.open;
        if (body3 < MORNING_STAR_BODY_PCT * range3) return false;

        // c3 must close above midpoint of c1's body
        double midBody1 = (c1.open + c1.close) / 2.0;
        return c3.close >= midBody1;
    }

    /**
     * Three White Soldiers:
     *   - Three consecutive bullish candles
     *   - Each opens within or above the prior candle's body
     *   - Each closes higher than the previous close
     *   - Each has a large body (>= 50% of its range)
     */
    public static boolean isThreeWhiteSoldiers(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close <= c1.open || c2.close <= c2.open || c3.close <= c3.open) return false;
        // Each closes higher
        if (c2.close <= c1.close || c3.close <= c2.close) return false;
        // Each opens within or above previous body
        if (c2.open < c1.open || c3.open < c2.open) return false;
        // Large bodies
        double range1 = c1.high - c1.low, range2 = c2.high - c2.low, range3 = c3.high - c3.low;
        if (range1 <= 0 || range2 <= 0 || range3 <= 0) return false;
        return (c1.close - c1.open) >= LARGE_BODY_PCT * range1
            && (c2.close - c2.open) >= LARGE_BODY_PCT * range2
            && (c3.close - c3.open) >= LARGE_BODY_PCT * range3;
    }

    /**
     * Rising Three Methods (5-candle continuation pattern):
     *   c1 – large bullish candle
     *   c2,c3,c4 – three small bearish/sideways candles, all within c1's range
     *   c5 – large bullish candle closing above c1's close
     */
    public static boolean isRisingThreeMethods(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3,
            HistoricalDataFetcher.Candle c4,
            HistoricalDataFetcher.Candle c5) {
        // c1 must be large bullish
        double range1 = c1.high - c1.low;
        if (range1 <= 0) return false;
        if (c1.close <= c1.open) return false;
        if ((c1.close - c1.open) < LARGE_BODY_PCT * range1) return false;

        // c2,c3,c4 must be small candles contained within c1's high/low
        for (HistoricalDataFetcher.Candle mid : new HistoricalDataFetcher.Candle[]{c2, c3, c4}) {
            if (mid.high > c1.high || mid.low < c1.low) return false;
        }

        // c5 must be large bullish and close above c1's close
        double range5 = c5.high - c5.low;
        if (range5 <= 0) return false;
        if (c5.close <= c5.open) return false;
        if ((c5.close - c5.open) < LARGE_BODY_PCT * range5) return false;
        return c5.close > c1.close;
    }

    /**
     * Strong Bullish candle (continuation): large body >= 60% of range, bullish.
     * Distinct from Marubozu — wicks are allowed.
     */
    public static boolean isStrongBullish(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close <= c.open) return false;
        return (c.close - c.open) >= STRONG_BODY_PCT * range;
    }

    /**
     * Shooting Star: large upper wick >= 55% of range, small body <= 25%,
     * tiny lower wick <= 15%. Bearish reversal signal at top of uptrend.
     */
    public static boolean isShootingStar(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double upperWick = c.high - Math.max(c.open, c.close);
        double lowerWick = Math.min(c.open, c.close) - c.low;
        return upperWick >= SHOOTING_STAR_UPPER_PCT * range
            && body      <= SHOOTING_STAR_BODY_PCT * range
            && lowerWick <= SHOOTING_STAR_LOWER_PCT * range;
    }

    /**
     * Inverted Hammer: bullish reversal signal appearing after a downtrend.
     * Characteristics:
     *   - Long upper wick >= 2x the body size (price tried to rally, sellers pushed back)
     *   - Small body (<= 25% of candle range) — open and close are close together
     *   - Tiny or no lower wick (<= 10% of range) — price held near the open/close
     *   - Body can be bullish or bearish; bullish body is a stronger signal
     *
     * Key distinction from Shooting Star: same shape but Inverted Hammer appears
     * after a downtrend (buyers starting to push back), whereas Shooting Star
     * appears after an uptrend (sellers rejecting higher prices).
     */
    public static boolean isInvertedHammer(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double upperWick = c.high - Math.max(c.open, c.close);
        double lowerWick = Math.min(c.open, c.close) - c.low;
        if (body <= 0) return false;
        // Upper wick must be at least 2x the body (dominant feature)
        // Body must be small (<= 25% of range)
        // Lower wick must be tiny (<= 10% of range)
        return upperWick >= 2.0 * body
            && body      <= SHOOTING_STAR_BODY_PCT * range
            && lowerWick <= 0.10 * range;
    }

    /**
     * Rising Window (Bullish Gap): current candle opens above the previous candle's high.
     * Strongest single sign of bullish continuation momentum.
     */
    public static boolean isRisingWindow(
            HistoricalDataFetcher.Candle previous,
            HistoricalDataFetcher.Candle current) {
        return current.open > previous.high;
    }

    /**
     * Bullish Mat Hold:
     *   - c1 is a large bullish candle
     *   - c2 is a small pullback candle (bearish or doji) that stays above c1's midpoint
     *   - c2's close is above (c1.open + c1.close) / 2
     */
    public static boolean isBullishMatHold(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2) {
        double range1 = c1.high - c1.low;
        if (range1 <= 0) return false;
        if (c1.close <= c1.open) return false; // c1 must be bullish
        if ((c1.close - c1.open) < LARGE_BODY_PCT * range1) return false; // c1 large body
        double midBody1 = (c1.open + c1.close) / 2.0;
        return c2.close > midBody1; // pullback stays above midpoint
    }

    /**
     * Upside Tasuki Gap:
     *   - c1 and c2 are both bullish with a gap between them (c2.open > c1.close)
     *   - c3 is bearish and partially fills the gap but does NOT close it
     *     (c3.close stays above c1.close)
     */
    public static boolean isUpsideTasukiGap(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close <= c1.open) return false; // c1 bullish
        if (c2.close <= c2.open) return false; // c2 bullish
        if (c2.open  <= c1.close) return false; // gap between c1 and c2
        if (c3.close >= c3.open) return false;  // c3 bearish
        // c3 partially fills gap but close stays above c1's close
        return c3.open >= c2.open && c3.close > c1.close;
    }

    /**
     * Gravestone Doji: open ≈ close ≈ low, long upper wick, tiny lower wick.
     * Bearish shape — sellers completely rejected the rally.
     */
    public static boolean isGravestoneDoji(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double lowerWick = Math.min(c.open, c.close) - c.low;
        return body      <= DOJI_BODY_PCT * range
            && lowerWick <= MARUBOZU_WICK_PCT * range;
    }

    /**
     * Long-Legged Doji: body <= 10% of range, both wicks >= 35% of range.
     * Extreme indecision — often precedes a significant move.
     */
    public static boolean isLongLeggedDoji(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        double body      = Math.abs(c.close - c.open);
        double upperWick = c.high - Math.max(c.open, c.close);
        double lowerWick = Math.min(c.open, c.close) - c.low;
        return body      <= DOJI_BODY_PCT * range
            && upperWick >= 0.35 * range
            && lowerWick >= 0.35 * range;
    }

    /**
     * Bullish Harami Cross:
     *   - Previous candle is large bearish (body >= 40% of range)
     *   - Current candle is a Doji fully inside the previous bearish body
     *   Stronger signal than regular BullHarami.
     */
    public static boolean isBullishHaramiCross(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (previous.close >= previous.open) return false; // previous bearish
        double prevRange = previous.high - previous.low;
        if (prevRange <= 0) return false;
        if ((previous.open - previous.close) < MORNING_STAR_BODY_PCT * prevRange) return false; // large bearish
        if (!isDoji(current)) return false; // current must be a doji
        // doji open/close both inside previous body
        double dojiBod = Math.max(current.open, current.close);
        double dojiBot = Math.min(current.open, current.close);
        return dojiBot >= previous.close && dojiBod <= previous.open;
    }

    /**
     * Separating Lines (bullish continuation):
     *   - Previous candle is bearish
     *   - Current candle is bullish and opens at (approximately) the same price as previous open
     *   - Gap-up open relative to previous close
     */
    public static boolean isSeparatingLines(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current bullish
        if (previous.close >= previous.open) return false; // previous bearish
        if (current.open   <= previous.close) return false; // must gap up from prev close
        // Opens near same price as previous open (within 0.2% tolerance)
        double avgOpen = (current.open + previous.open) / 2.0;
        if (avgOpen <= 0) return false;
        return Math.abs(current.open - previous.open) / avgOpen <= 0.002;
    }

    /**
     * Bullish Counter Attack:
     *   - Previous candle is large bearish
     *   - Current candle is bullish and closes at approximately the same level as previous close
     *   Signals buyers fully absorbed the sell-off.
     */
    public static boolean isBullishCounterAttack(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  <= current.open)  return false; // current bullish
        if (previous.close >= previous.open) return false; // previous bearish
        // Closes at approximately same level as previous close (within 0.2%)
        double avgClose = (current.close + previous.close) / 2.0;
        if (avgClose <= 0) return false;
        return Math.abs(current.close - previous.close) / avgClose <= 0.002;
    }

    /**
     * Three Inside Up (3-candle bullish reversal — very reliable):
     *   c1 – large bearish candle
     *   c2 – small bullish candle inside c1's body (BullHarami)
     *   c3 – bullish confirmation that closes above c1's open
     */
    public static boolean isThreeInsideUp(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close >= c1.open) return false; // c1 bearish
        if (c2.close <= c2.open) return false; // c2 bullish
        if (c3.close <= c3.open) return false; // c3 bullish
        // c2 body inside c1 body (BullHarami requirement)
        if (c2.open  < c1.close || c2.close > c1.open) return false;
        // c3 closes above c1's open (confirms reversal)
        return c3.close > c1.open;
    }

    /**
     * Three Outside Up (3-candle bullish reversal — very reliable):
     *   c1 – bearish candle
     *   c2 – bullish engulfing candle (fully engulfs c1)
     *   c3 – bullish confirmation that closes higher than c2
     */
    public static boolean isThreeOutsideUp(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close >= c1.open) return false; // c1 bearish
        if (c2.close <= c2.open) return false; // c2 bullish
        if (c3.close <= c3.open) return false; // c3 bullish
        // c2 engulfs c1
        if (c2.open > c1.close || c2.close < c1.open) return false;
        // c3 closes above c2's close
        return c3.close > c2.close;
    }

    /**
     * Abandoned Baby (rare but extremely strong bullish reversal):
     *   c1 – large bearish candle
     *   c2 – doji that gaps down below c1's low (entirely below c1)
     *   c3 – large bullish candle that gaps up above c2's high
     */
    public static boolean isAbandonedBaby(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close >= c1.open) return false; // c1 bearish
        if (c3.close <= c3.open) return false; // c3 bullish
        if (!isDoji(c2)) return false;          // c2 must be doji
        // c2 gaps down below c1 (c2's high is below c1's low)
        if (c2.high >= c1.low) return false;
        // c3 gaps up above c2 (c3's low is above c2's high)
        return c3.low > c2.high;
    }

    /**
     * Three Line Strike (4-candle bullish reversal):
     *   c1, c2, c3 – three consecutive bearish candles each closing lower
     *   c4 – one large bullish candle that opens below c3's close and
     *         closes above c1's open (engulfs all three)
     */
    public static boolean isThreeLineStrike(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3,
            HistoricalDataFetcher.Candle c4) {
        // c1, c2, c3 all bearish, each closing lower
        if (c1.close >= c1.open || c2.close >= c2.open || c3.close >= c3.open) return false;
        if (c2.close >= c1.close || c3.close >= c2.close) return false;
        // c4 bullish, opens at or below c3's close, closes above c1's open
        if (c4.close <= c4.open) return false;
        if (c4.open  > c3.close) return false;
        return c4.close >= c1.open;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /** Returns all 15-min candles from the most recent trading day before targetDay. */
    private static List<HistoricalDataFetcher.Candle> getPreviousDayCandles(
            List<HistoricalDataFetcher.Candle> candles15m,
            LocalDate targetDay) {
        // Find the latest date that is strictly before targetDay
        LocalDate prevDay = null;
        for (HistoricalDataFetcher.Candle c : candles15m) {
            LocalDate d = ZonedDateTime.parse(c.timestamp).toLocalDate();
            if (d.isBefore(targetDay)) {
                prevDay = d; // keep updating; will end up as the last date before targetDay
            }
        }

        if (prevDay == null) return new ArrayList<>();

        final LocalDate pd = prevDay;
        List<HistoricalDataFetcher.Candle> result = new ArrayList<>();
        for (HistoricalDataFetcher.Candle c : candles15m) {
            if (ZonedDateTime.parse(c.timestamp).toLocalDate().equals(pd)) {
                result.add(c);
            }
        }
        return result;
    }

     /**
     * Bearish Marubozu: close < open, both upper and lower wicks <= 5% of range.
     */
    public static boolean isBearishMarubozu(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close >= c.open) return false;          // must be bearish
        double upperWick = c.high - c.open;
        double lowerWick = c.close - c.low;
        return upperWick <= MARUBOZU_WICK_PCT * range
            && lowerWick <= MARUBOZU_WICK_PCT * range;
    }

    /**
     * Very bearish candle (bearish pin bar):
     *   - Bearish body (close < open)
     *   - Upper wick >= 55% of total range
     *   - Lower wick <= 15% of total range
     */
    public static boolean isVeryBearishCandle(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close >= c.open) return false;           // bearish body required
        double upperWick = c.high - c.open;            // open is top of bearish body
        double lowerWick = c.close - c.low;
        return upperWick >= BIG_LOWER_WICK_PCT * range
            && lowerWick <= SMALL_UPPER_WICK_PCT * range;
    }

    /**
     * Bearish Engulfing:
     *   - Previous candle is bullish (close > open)
     *   - Current candle is bearish (close < open)
     *   - Current bearish body fully contains previous bullish body
     */
    public static boolean isBearishEngulfing(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current must be bearish
        if (previous.close <= previous.open) return false; // previous must be bullish
        // current body (open..close) engulfs previous body (open..close)
        return current.open  >= previous.close
            && current.close <= previous.open;
    }

    /**
     * Bearish Kicker: previous candle is bullish, current candle opens below
     * the previous candle's open (gap-down) and is bearish.
     */
    public static boolean isBearishKicker(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current bearish
        if (previous.close <= previous.open) return false; // previous bullish
        return current.open < previous.open;               // gap-down
    }

    /**
     * Dark Cloud Cover:
     *   - Previous candle is bullish
     *   - Current opens above previous high
     *   - Current closes below midpoint of previous bullish body
     *   - Current is bearish
     */
    public static boolean isDarkCloudCover(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current bearish
        if (previous.close <= previous.open) return false; // previous bullish
        double prevMid = (previous.open + previous.close) / 2.0;
        return current.open  > previous.high
            && current.close < prevMid
            && current.close > previous.open;  // hasn't fully engulfed
    }

    /**
     * Tweezer Top: two candles (first bullish, second bearish) sharing the same high.
     * Highs are within 0.1% of each other.
     */
    public static boolean isTweezerTop(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current bearish
        if (previous.close <= previous.open) return false; // previous bullish
        double avgHigh = (previous.high + current.high) / 2.0;
        if (avgHigh <= 0) return false;
        return Math.abs(current.high - previous.high) / avgHigh <= TWEEZER_TOLERANCE_PCT;
    }

    /**
     * Bearish Harami:
     *   - Previous candle is a large bullish candle
     *   - Current small bearish body is fully contained within the previous body
     */
    public static boolean isBearishHarami(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current bearish
        if (previous.close <= previous.open) return false; // previous bullish
        // current body fully inside previous body
        return current.open  <= previous.close
            && current.close >= previous.open;
    }

    /**
     * Bearish Harami Cross: previous bullish, current doji inside previous body
     */
    public static boolean isBearishHaramiCross(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (previous.close <= previous.open) return false; // previous bullish
        double prevRange = previous.high - previous.low;
        if (prevRange <= 0) return false;
        if ((previous.close - previous.open) < MORNING_STAR_BODY_PCT * prevRange) return false; // large bullish
        if (!isDoji(current)) return false; // current must be a doji
        // doji open/close both inside previous body
        double dojiTop = Math.max(current.open, current.close);
        double dojiBot = Math.min(current.open, current.close);
        return dojiTop <= previous.close && dojiBot >= previous.open;
    }

    /**
     * Separating Lines (Bearish): current and previous both bearish, open near previous open, gap down.
     */
    public static boolean isSeparatingLinesBearish(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current bearish
        if (previous.close <= previous.open) return false; // previous bullish
        if (current.open   >= previous.close) return false; // must gap down from prev close
        // Opens near same price as previous open (within 0.2% tolerance)
        double avgOpen = (current.open + previous.open) / 2.0;
        if (avgOpen <= 0) return false;
        return Math.abs(current.open - previous.open) / avgOpen <= 0.002;
    }

    /**
     * Bearish Counter Attack: current and previous both bearish, closes at same level.
     */
    public static boolean isBearishCounterAttack(
            HistoricalDataFetcher.Candle current,
            HistoricalDataFetcher.Candle previous) {
        if (current.close  >= current.open)  return false; // current bearish
        if (previous.close <= previous.open) return false; // previous bullish
        // Closes at approximately same level as previous close (within 0.2%)
        double avgClose = (current.close + previous.close) / 2.0;
        if (avgClose <= 0) return false;
        return Math.abs(current.close - previous.close) / avgClose <= 0.002;
    }

    /**
     * Falling Window: current opens below previous low (gap down)
     */
    public static boolean isFallingWindow(
            HistoricalDataFetcher.Candle previous,
            HistoricalDataFetcher.Candle current) {
        return current.open < previous.low;
    }

    /**
     * Bearish Mat Hold: c1 large bearish, c2 closes below midpoint of c1
     */
    public static boolean isBearishMatHold(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2) {
        double range1 = c1.high - c1.low;
        if (range1 <= 0) return false;
        if (c1.close >= c1.open) return false; // c1 must be bearish
        if ((c1.open - c1.close) < LARGE_BODY_PCT * range1) return false; // c1 large body
        double midBody1 = (c1.open + c1.close) / 2.0;
        return c2.close < midBody1; // pullback closes below midpoint
    }

    /**
     * Evening Star (3-candle bearish reversal):
     *   c1 – large bullish candle    (body >= 40% of range)
     *   c2 – small-body star candle  (doji or body <= 30% of range)
     *   c3 – large bearish candle    (body >= 40% of range, closes below midpoint of c1)
     */
    public static boolean isEveningStar(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        double range1 = c1.high - c1.low;
        double range2 = c2.high - c2.low;
        double range3 = c3.high - c3.low;
        if (range1 <= 0 || range3 <= 0) return false;

        // c1: large bullish
        double body1 = c1.close - c1.open;
        if (body1 < MORNING_STAR_BODY_PCT * range1) return false;

        // c2: small body (star)
        double starBodyRatio = range2 > 0 ? Math.abs(c2.close - c2.open) / range2 : 1.0;
        if (starBodyRatio > 0.30) return false;

        // c3: large bearish
        double body3 = c3.open - c3.close;
        if (body3 < MORNING_STAR_BODY_PCT * range3) return false;

        // c3 must close below midpoint of c1's body
        double midBody1 = (c1.open + c1.close) / 2.0;
        return c3.close <= midBody1;
    }

    /**
     * Three Black Crows:
     *   - Three consecutive bearish candles
     *   - Each opens within or below the prior candle's body
     *   - Each closes lower than the previous close
     *   - Each has a large body (>= 50% of its range)
     */
    public static boolean isThreeBlackCrows(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close >= c1.open || c2.close >= c2.open || c3.close >= c3.open) return false;
        // Each closes lower
        if (c2.close >= c1.close || c3.close >= c2.close) return false;
        // Each opens within or below previous body
        if (c2.open > c1.open || c3.open > c2.open) return false;
        // Large bodies
        double range1 = c1.high - c1.low, range2 = c2.high - c2.low, range3 = c3.high - c3.low;
        if (range1 <= 0 || range2 <= 0 || range3 <= 0) return false;
        return (c1.open - c1.close) >= LARGE_BODY_PCT * range1
            && (c2.open - c2.close) >= LARGE_BODY_PCT * range2
            && (c3.open - c3.close) >= LARGE_BODY_PCT * range3;
    }

    /**
     * Downside Tasuki Gap:
     *   - c1, c2 bearish with gap down
     *   - c3 bullish, partially fills gap but close stays below c1's close
     */
    public static boolean isDownsideTasukiGap(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close >= c1.open) return false; // c1 bearish
        if (c2.close >= c2.open) return false; // c2 bearish
        if (c2.open  >= c1.close) return false; // gap between c1 and c2
        if (c3.close <= c3.open) return false;  // c3 bullish
        // c3 partially fills gap but close stays below c1's close
        return c3.open <= c2.open && c3.close < c1.close;
    }

    /**
     * Three Inside Down:
     *   - c1 bullish, c2 bearish body inside c1, c3 bearish closes below c1's open
     */
    public static boolean isThreeInsideDown(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close <= c1.open) return false; // c1 bullish
        if (c2.close >= c2.open) return false; // c2 bearish
        if (c3.close >= c3.open) return false; // c3 bearish
        // c2 body inside c1 body (BearHarami requirement)
        if (c2.open  > c1.close || c2.close < c1.open) return false;
        // c3 closes below c1's open (confirms reversal)
        return c3.close < c1.open;
    }

    /**
     * Three Outside Down:
     *   - c1 bullish, c2 bearish engulfs c1, c3 bearish closes below c2's close
     */
    public static boolean isThreeOutsideDown(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close <= c1.open) return false; // c1 bullish
        if (c2.close >= c2.open) return false; // c2 bearish
        if (c3.close >= c3.open) return false; // c3 bearish
        // c2 engulfs c1
        if (c2.open < c1.close || c2.close > c1.open) return false;
        // c3 closes below c2's close
        return c3.close < c2.close;
    }

    /**
     * Abandoned Baby (Bearish): c1 bullish, c2 doji gaps up, c3 bearish gaps down
     */
    public static boolean isAbandonedBabyBear(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3) {
        if (c1.close <= c1.open) return false; // c1 bullish
        if (c3.close >= c3.open) return false; // c3 bearish
        if (!isDoji(c2)) return false;          // c2 must be doji
        // c2 gaps up above c1 (c2's low is above c1's high)
        if (c2.low <= c1.high) return false;
        // c3 gaps down below c2 (c3's high is below c2's low)
        return c3.high < c2.low;
    }

    /**
     * Three Line Strike (Bearish): 3 bearish, then bullish reversal
     */
    public static boolean isThreeLineStrikeBear(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3,
            HistoricalDataFetcher.Candle c4) {
        // c1, c2, c3 all bullish, each closing higher
        if (c1.close <= c1.open || c2.close <= c2.open || c3.close <= c3.open) return false;
        if (c2.close <= c1.close || c3.close <= c2.close) return false;
        // c4 bearish, opens at or above c3's close, closes below c1's open
        if (c4.close >= c4.open) return false;
        if (c4.open  < c3.close) return false;
        return c4.close <= c1.open;
    }

    /**
     * Falling Three Methods (5-candle continuation pattern):
     *   c1 – large bearish candle
     *   c2,c3,c4 – three small bullish/sideways candles, all within c1's range
     *   c5 – large bearish candle closing below c1's close
     */
    public static boolean isFallingThreeMethods(
            HistoricalDataFetcher.Candle c1,
            HistoricalDataFetcher.Candle c2,
            HistoricalDataFetcher.Candle c3,
            HistoricalDataFetcher.Candle c4,
            HistoricalDataFetcher.Candle c5) {
        // c1 must be large bearish
        double range1 = c1.high - c1.low;
        if (range1 <= 0) return false;
        if (c1.close >= c1.open) return false;
        if ((c1.open - c1.close) < LARGE_BODY_PCT * range1) return false;

        // c2,c3,c4 must be small candles contained within c1's high/low
        for (HistoricalDataFetcher.Candle mid : new HistoricalDataFetcher.Candle[]{c2, c3, c4}) {
            if (mid.high > c1.high || mid.low < c1.low) return false;
        }

        // c5 must be large bearish and close below c1's close
        double range5 = c5.high - c5.low;
        if (range5 <= 0) return false;
        if (c5.close >= c5.open) return false;
        if ((c5.open - c5.close) < LARGE_BODY_PCT * range5) return false;
        return c5.close < c1.close;
    }

        /**
     * Strong Bearish candle (continuation): large body >= 60% of range, bearish.
     * Distinct from Marubozu — wicks are allowed.
     */
    public static boolean isStrongBearish(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close >= c.open) return false;
        return (c.open - c.close) >= STRONG_BODY_PCT * range;
    }

    /**
     * Bearish Belt Hold: opens at/near the high (no upper wick), bearish close near low.
     * Upper wick <= 5% of range, lower wick <= 10% of range, bearish body.
     */
    public static boolean isBearishBeltHold(HistoricalDataFetcher.Candle c) {
        double range = c.high - c.low;
        if (range <= 0) return false;
        if (c.close >= c.open) return false;           // must be bearish
        double upperWick = c.high - c.open;
        double lowerWick = c.close - c.low;
        return upperWick <= BELT_HOLD_LOWER_WICK_PCT * range
            && lowerWick <= BELT_HOLD_UPPER_WICK_PCT * range;
    }
}
