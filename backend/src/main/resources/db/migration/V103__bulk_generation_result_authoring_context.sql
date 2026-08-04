ALTER TABLE bulk_generation_result
    ADD COLUMN domain_context VARCHAR(64),
    ADD COLUMN learner_level VARCHAR(32);
