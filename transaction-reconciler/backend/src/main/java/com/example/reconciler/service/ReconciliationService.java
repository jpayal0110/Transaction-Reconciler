package com.example.reconciler.service;

import com.example.reconciler.model.ReferenceTransaction;
import com.example.reconciler.model.ReconciliationResult;
import com.example.reconciler.model.Transaction;
import com.example.reconciler.repository.ReferenceRepository;
import com.example.reconciler.repository.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.transaction.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final TransactionRepository transactionRepository;
    private final ReferenceRepository referenceRepository;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 ReferenceRepository referenceRepository) {
        this.transactionRepository = transactionRepository;
        this.referenceRepository = referenceRepository;
    }

    @Transactional
    public void ingestTransactionsCsv(MultipartFile file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        List<Transaction> list = new ArrayList<>();
        for (CSVRecord r : csvParser) {
            String txnId = r.get("txn_id");
            String source = r.isMapped("source") ? r.get("source") : null;
            String amountStr = r.get("amount");
            String dateStr = r.get("txn_date");
            BigDecimal amount = amountStr == null || amountStr.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountStr);
            LocalDate date = (dateStr == null || dateStr.isBlank()) ? null : LocalDate.parse(dateStr);
            Transaction t = new Transaction(txnId, source, amount, date);
            list.add(t);
        }
        transactionRepository.saveAll(list);
    }

    @Transactional
    public void ingestReferenceCsv(MultipartFile file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        List<ReferenceTransaction> list = new ArrayList<>();
        for (CSVRecord r : csvParser) {
            String refTxnId = r.get("ref_txn_id");
            String source = r.isMapped("source") ? r.get("source") : null;
            String amountStr = r.get("amount");
            String dateStr = r.get("txn_date");
            BigDecimal amount = amountStr == null || amountStr.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountStr);
            LocalDate date = (dateStr == null || dateStr.isBlank()) ? null : LocalDate.parse(dateStr);
            ReferenceTransaction rt = new ReferenceTransaction(refTxnId, source, amount, date);
            list.add(rt);
        }
        referenceRepository.saveAll(list);
    }

    @Transactional
    public ReconciliationResult reconcileAll() {
        List<Transaction> txns = transactionRepository.findAll();
        List<ReferenceTransaction> refs = referenceRepository.findAll();

        Map<Long, Boolean> refMatched = new HashMap<>();
        List<Map<String, Object>> matches = new ArrayList<>();

        Map<String, ReferenceTransaction> refsById = refs.stream()
                .filter(r -> r.getRefTxnId() != null && !r.getRefTxnId().isBlank())
                .collect(Collectors.toMap(ReferenceTransaction::getRefTxnId, r -> r, (a,b)->a));

        Multimap<String, ReferenceTransaction> refsByAmountDate = new Multimap<>();
        for (ReferenceTransaction r : refs) {
            String key = (r.getAmount() != null ? r.getAmount().toPlainString() : "NA") + "|" + (r.getTxnDate() != null ? r.getTxnDate().toString() : "NA");
            refsByAmountDate.put(key, r);
        }

        int matchedCount = 0;
        for (Transaction t : txns) {
            boolean matched = false;

            if (t.getTxnId() != null && refsById.containsKey(t.getTxnId())) {
                ReferenceTransaction r = refsById.get(t.getTxnId());
                matches.add(Map.of("txn", t, "reference", r, "rule", "txnId"));
                matched = true;
                matchedCount++;
                refMatched.put(r.getId(), true);
                continue;
            }

            String key = (t.getAmount() != null ? t.getAmount().toPlainString() : "NA") + "|" + (t.getTxnDate() != null ? t.getTxnDate().toString() : "NA");
            List<ReferenceTransaction> maybe = refsByAmountDate.get(key);
            if (!maybe.isEmpty()) {
                ReferenceTransaction r = maybe.get(0);
                matches.add(Map.of("txn", t, "reference", r, "rule", "amount+date"));
                matched = true;
                matchedCount++;
                refMatched.put(r.getId(), true);
                refsByAmountDate.removeOne(key, r);
                continue;
            }

            if (!matched) {
                matches.add(Map.of("txn", t, "reference", null, "rule", "unmatched"));
            }
        }

        List<ReferenceTransaction> unmatchedRefs = refs.stream()
                .filter(r -> !refMatched.containsKey(r.getId()))
                .toList();

        ReconciliationResult result = new ReconciliationResult();
        result.setTotalTransactions(txns.size());
        result.setTotalReferences(refs.size());
        result.setMatched(matchedCount);
        result.setUnmatchedTransactions(txns.size() - matchedCount);
        result.setUnmatchedReferences(unmatchedRefs.size());

        List<Map<String,Object>> simpleMatches = matches.stream().map(m -> {
            Transaction tx = (Transaction) m.get("txn");
            ReferenceTransaction ref = (ReferenceTransaction) m.get("reference");
            Map<String,Object> mm = new HashMap<>();
            mm.put("txnId", tx.getTxnId());
            mm.put("txnAmount", tx.getAmount());
            mm.put("txnDate", tx.getTxnDate());
            mm.put("referenceId", ref != null ? ref.getRefTxnId() : null);
            mm.put("referenceAmount", ref != null ? ref.getAmount() : null);
            mm.put("rule", m.get("rule"));
            return mm;
        }).toList();

        result.setMatches(simpleMatches);
        return result;
    }

    private static class Multimap<K,V> {
        private final Map<K, List<V>> map = new HashMap<>();
        public void put(K k, V v) { map.computeIfAbsent(k, kk -> new ArrayList<>()).add(v); }
        public List<V> get(K k) { return map.getOrDefault(k, Collections.emptyList()); }
        public void removeOne(K k, V v) { List<V> l = map.get(k); if (l!=null) l.remove(v); }
    }
}
