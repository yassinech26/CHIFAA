package tn.chifaa.knowledge;

import java.util.List;

public record ItemPage(List<ItemSummary> items, String nextAfter) {}
