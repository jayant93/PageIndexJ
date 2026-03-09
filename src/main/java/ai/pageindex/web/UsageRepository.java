package ai.pageindex.web;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsageRepository extends MongoRepository<UsageRecord, String> {}
