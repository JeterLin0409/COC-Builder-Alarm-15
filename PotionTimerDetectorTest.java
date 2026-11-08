package com.example.builderpotionalarm;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class PotionTimerDetectorTest {
    private static final int W=1536, H=709;
    @Test public void readsCircledPotionTimer() {
        List<PotionTimerDetector.Candidate> data=Arrays.asList(
                new PotionTimerDetector.Candidate("Available", 970,343),
                new PotionTimerDetector.Candidate("59m 57s",965,393),
                new PotionTimerDetector.Candidate("4h 34m",1425,84),
                new PotionTimerDetector.Candidate("1h 37m",968,234));
        assertEquals(3597, PotionTimerDetector.detect(data,W,H));
    }
    @Test public void supportsSplitFragments() {
        List<PotionTimerDetector.Candidate> data=Arrays.asList(
                new PotionTimerDetector.Candidate("59m",943,391),
                new PotionTimerDetector.Candidate("57s",991,391));
        assertEquals(3597, PotionTimerDetector.detect(data,W,H));
    }
    @Test public void doesNotReadGoblinOrShieldAsPotion() {
        List<PotionTimerDetector.Candidate> data=Arrays.asList(
                new PotionTimerDetector.Candidate("1h 37m",968,234),
                new PotionTimerDetector.Candidate("14h 1m",320,22),
                new PotionTimerDetector.Candidate("59m",963,392));
        assertEquals(-1, PotionTimerDetector.detect(data,W,H));
    }

    @Test public void noVisiblePotionDefaultsToZero() {
        List<PotionTimerDetector.Candidate> data=Arrays.asList(
                new PotionTimerDetector.Candidate("Available",970,343),
                new PotionTimerDetector.Candidate("4h 34m",1425,84));
        PotionTimerDetector.Assessment a=PotionTimerDetector.assess(data,W,H);
        assertEquals(PotionTimerDetector.State.ABSENT,a.state);
        assertEquals(0,a.seconds);
        assertEquals(4*3600+34*60,TimerMath.realSecondsUntilFinished(4*3600+34*60,a.seconds));
    }
    @Test public void partialPotionDoesNotAssumeZero() {
        List<PotionTimerDetector.Candidate> data=Arrays.asList(
                new PotionTimerDetector.Candidate("59m",965,393));
        PotionTimerDetector.Assessment a=PotionTimerDetector.assess(data,W,H);
        assertEquals(PotionTimerDetector.State.UNCERTAIN,a.state);
        assertEquals(-1,a.seconds);
    }
    @Test public void visiblePotionIsDetected() {
        List<PotionTimerDetector.Candidate> data=Arrays.asList(
                new PotionTimerDetector.Candidate("59m 57s",965,393));
        PotionTimerDetector.Assessment a=PotionTimerDetector.assess(data,W,H);
        assertEquals(PotionTimerDetector.State.DETECTED,a.state);
        assertEquals(3597,a.seconds);
    }
    @Test public void correctExampleCompletionTimes() {
        long p=59*60+57;
        assertEquals(27*60+24,TimerMath.realSecondsUntilFinished(4*3600+34*60,p));
        assertEquals(5*3600+60+27,TimerMath.realSecondsUntilFinished(14*3600+60,p));
    }
}
