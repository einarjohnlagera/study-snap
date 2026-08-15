alter table challenge_quiz_question_bank
    drop constraint uq_challenge_quiz_question_bank_user_pack_key;

alter table challenge_quiz_question_bank
    add constraint uq_challenge_quiz_question_bank_user_pack_key
        unique nulls not distinct (user_id, study_pack_id, question_key, learner_level);
