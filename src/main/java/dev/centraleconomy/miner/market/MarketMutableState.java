package dev.centraleconomy.miner.market;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Serializable logical state. No villager/NPC id is used in quota or stock keys. */
public final class MarketMutableState {
    private long initializedCycle = Long.MIN_VALUE;
    private long cumulativeTurnoverEmeralds;
    private final Map<QuotaKey, QuotaUsage> quotaUsage = new HashMap<>();
    private final Map<JobQuotaKey, JobQuotaUsage> jobQuotaUsage = new HashMap<>();
    private final Map<String, Integer> retailStock = new HashMap<>();
    private final Map<String, Integer> retailOverflowStock = new HashMap<>();
    private final Set<String> infrastructureFlags = new HashSet<>();
    private final Map<UUID, WorkstationClaim> workstationClaims = new HashMap<>();

    public long initializedCycle() { return initializedCycle; }
    public void initializedCycle(long value) { initializedCycle = value; }
    public long cumulativeTurnoverEmeralds() { return cumulativeTurnoverEmeralds; }
    public void addTurnover(long emeralds) { cumulativeTurnoverEmeralds = Math.addExact(cumulativeTurnoverEmeralds, emeralds); }
    public void cumulativeTurnoverEmeralds(long value) { cumulativeTurnoverEmeralds = Math.max(0, value); }
    public Map<QuotaKey, QuotaUsage> quotaUsage() { return quotaUsage; }
    public Map<JobQuotaKey, JobQuotaUsage> jobQuotaUsage() { return jobQuotaUsage; }
    public Map<String, Integer> retailStock() { return retailStock; }
    public Map<String, Integer> retailOverflowStock() { return retailOverflowStock; }
    public Set<String> infrastructureFlags() { return infrastructureFlags; }
    public Map<UUID, WorkstationClaim> workstationClaims() { return workstationClaims; }
}
