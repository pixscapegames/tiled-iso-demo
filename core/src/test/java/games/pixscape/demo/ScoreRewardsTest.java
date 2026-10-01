package games.pixscape.demo;

import org.junit.Assert;
import org.junit.Test;

public class ScoreRewardsTest {
    @Test
    public void oneRewardIsDisplayedOnlyAtArrivalAndOnlyOnce() {
        ScoreRewards rewards = new ScoreRewards();
        ScoreRewards.Ticket ticket = rewards.earn(10);

        Assert.assertEquals(10, rewards.earned());
        Assert.assertEquals(0, rewards.displayed());
        Assert.assertTrue(rewards.display(ticket));
        Assert.assertFalse(rewards.display(ticket));
        Assert.assertEquals(10, rewards.displayed());
    }

    @Test
    public void concurrentRewardsCanArriveOutOfOrder() {
        ScoreRewards rewards = new ScoreRewards();
        ScoreRewards.Ticket first = rewards.earn(10);
        ScoreRewards.Ticket second = rewards.earn(20);

        Assert.assertTrue(rewards.display(second));
        Assert.assertEquals(20, rewards.displayed());
        Assert.assertTrue(rewards.display(first));
        Assert.assertEquals(30, rewards.displayed());
    }

    @Test
    public void interruptionFlushesTheOldSessionWithoutLeakingIntoTheNext() {
        ScoreRewards rewards = new ScoreRewards();
        ScoreRewards.Ticket interrupted = rewards.earn(10);

        Assert.assertTrue(rewards.flush());
        Assert.assertEquals(10, rewards.displayed());
        rewards.newSession();
        Assert.assertFalse(rewards.display(interrupted));
        Assert.assertEquals(0, rewards.earned());
        Assert.assertEquals(0, rewards.displayed());
    }

    @Test
    public void missingEffectOrTargetCanDisplayImmediately() {
        ScoreRewards rewards = new ScoreRewards();
        ScoreRewards.Ticket noEffect = rewards.earn(10);
        ScoreRewards.Ticket noTarget = rewards.earn(10);

        Assert.assertTrue(rewards.display(noEffect));
        Assert.assertTrue(rewards.display(noTarget));
        Assert.assertEquals(rewards.earned(), rewards.displayed());
    }
}
