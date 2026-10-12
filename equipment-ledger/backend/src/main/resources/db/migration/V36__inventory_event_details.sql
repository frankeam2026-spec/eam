-- Keep action labels compact; retain full closure explanations separately.
ALTER TABLE inv_document_event ADD details_json JSON NULL;
