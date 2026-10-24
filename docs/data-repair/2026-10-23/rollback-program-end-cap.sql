-- Restore the captured 2027-06-30 values only if every row is still at its post-repair version.
USE class_deduction_system_v2;

DROP TEMPORARY TABLE IF EXISTS repair_program_end_cap_rollback_ids;
CREATE TEMPORARY TABLE repair_program_end_cap_rollback_ids (
    enrollment_id BIGINT PRIMARY KEY,
    expected_version INT NOT NULL
) ENGINE=MEMORY;
INSERT INTO repair_program_end_cap_rollback_ids VALUES
        (2096682286998708225, 4),
        (2105089103030128642, 1),
        (2100822672281260034, 3),
        (2098443384575754241, 3),
        (2102509263018123266, 2),
        (2100788576771461122, 2),
        (2100790626473299970, 3),
        (2096612086689390594, 4),
        (2100789960837255169, 3),
        (2100578481106059265, 3),
        (2102596636779307009, 3),
        (2100592247881420801, 2),
        (2100591536259026945, 3),
        (2100794271818403842, 4),
        (2100587671652487170, 3),
        (2100599560017104898, 2),
        (2103530063699456001, 1),
        (2102415450127851521, 2),
        (2100615801041670145, 2),
        (2100617499982254082, 2),
        (2100618066032939010, 2),
        (2097326398512893953, 3),
        (2100807126726823937, 2),
        (2100809064767901698, 2),
        (2100809543094718465, 3),
        (2100810214216912897, 4),
        (2100810812068810755, 4),
        (2100637817450876930, 2),
        (2100812167672057858, 4),
        (2100811447493283841, 2),
        (2100643213590745090, 4),
        (2100811776800673793, 2),
        (2102416607697035265, 2),
        (2100678013311602690, 3),
        (2100693491111251970, 3),
        (2097324178585227265, 2),
        (2100697622886567937, 2),
        (2100698711140683778, 3),
        (2100572701925572610, 2),
        (2100716314575851522, 2),
        (2102422040323153922, 3),
        (2096974048820551681, 7),
        (2100718576752742401, 2),
        (2100732906072264706, 4),
        (2100788795579912194, 5),
        (2097399188519710721, 2),
        (2100739915618275329, 4),
        (2100743552511856641, 4),
        (2102403527567073281, 2),
        (2100817404105412610, 2),
        (2100818447497252865, 4),
        (2096647755755249665, 3),
        (2100746276469035010, 2),
        (2100784103734988802, 3),
        (2100784197179887618, 3),
        (2097398706493517825, 4),
        (2102066211505905665, 5),
        (2100785383211618305, 2),
        (2100786059333423106, 2),
        (2096973531201495041, 5),
        (2102599472078479361, 4),
        (2100820431252049921, 4);

DROP PROCEDURE IF EXISTS rollback_program_end_cap_20261023;
DELIMITER //
CREATE PROCEDURE rollback_program_end_cap_20261023()
BEGIN
    DECLARE v_changed_rows INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    UPDATE class_enrollment e
    JOIN repair_program_end_cap_rollback_ids ids ON ids.enrollment_id = e.id
    SET e.expiry_date = '2027-06-30', e.version = e.version + 1
    WHERE e.expiry_date = '2027-06-28'
      AND e.version = ids.expected_version
      AND e.status = 'ACTIVE' AND e.is_deleted = 0;

    SET v_changed_rows = ROW_COUNT();
    IF v_changed_rows <> 62 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Rollback stopped: expected exactly 62 unchanged program-cap rows.';
    END IF;
    COMMIT;
END//
DELIMITER ;

CALL rollback_program_end_cap_20261023();
DROP PROCEDURE rollback_program_end_cap_20261023;
DROP TEMPORARY TABLE repair_program_end_cap_rollback_ids;
