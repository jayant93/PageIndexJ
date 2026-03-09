package ai.pageindex.web;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface IndexedDocRepository extends MongoRepository<IndexedDoc, String> {

    Optional<IndexedDoc> findByDocKey(String docKey);

    boolean existsByDocKey(String docKey);

    List<IndexedDoc> findAllByOrderByIndexedAtDesc();
}
