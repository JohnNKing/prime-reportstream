/*
 * The Flyway tool applies this migration to create the database.
 *
 * Follow this style guide https://about.gitlab.com/handbook/business-ops/data-team/platform/sql-style-guide/
 * use VARCHAR(63) for names in organization and schema
 * 
 * scanned on https://online.sqlfluff.com with Dialect: snowflake
 * formatted in DBeaver per gitlab style guide
 *
 * This script update patient postal codes to match HHS safe harbor guidance for PII
 *
 * Zip code regex from http://unicode.org/Public/cldr/26.0.1/core.zip
 *
 */

-- zero out malformed postal codes, must be ##### or #####-#### or X#X #X# (Canadian)
UPDATE
  covid_result_metadata
SET
  patient_postal_code = '00000'
WHERE
  patient_postal_code !~ '^[0-9]{5}(?:-[0-9]{4})?$'
  AND
patient_postal_code !~ '[ABCEGHJKLMNPRSTVXY]\d[ABCEGHJ-NPRSTV-Z][ ]?\d[ABCEGHJ-NPRSTV-Z]\d';

-- zero out restricted zip codes e.g. 10244-4775 -> 00000
UPDATE
  covid_result_metadata crm
SET
  patient_postal_code = '00000'
FROM
  lookup_table_row ltr
INNER JOIN
	lookup_table_version ltv
ON
  ltv.lookup_table_version_id = ltr.lookup_table_version_id
WHERE
  substring(crm.patient_postal_code FOR 3) = ltr.data->>'patient_zip'
  AND
  ltv.table_name = 'restricted_zip_code';

-- zero out last 2 digits of all other 5 digit zip code, e.g. 12345 -> 12300
UPDATE
  covid_result_metadata crm
SET
  patient_postal_code =
    concat(substring(crm.patient_postal_code, 1, LENGTH(crm.patient_postal_code) - 2),
  '00')
WHERE
    crm.patient_postal_code <> '00000'
  AND
  crm.patient_postal_code ~ '^[0-9]{5}$' ;

-- zero out last 6 digits of all other 9 digit zip code, e.g. 12345-6789 -> 12300-0000
UPDATE
  covid_result_metadata
SET
  patient_postal_code =
    concat(substring(patient_postal_code, 1, LENGTH(patient_postal_code) - 7),
  '00-0000')
WHERE
  patient_postal_code ~ '^[0-9]{5}-[0-9]{4}$' ;

-- zero out last 3 digits of Canadian Postal codes, e.g. A1A 1A1 -> A1A 000
UPDATE
  covid_result_metadata
SET
  patient_postal_code =
    concat(substring(patient_postal_code, 1, LENGTH(patient_postal_code) - 3),
  '000')
WHERE
  patient_postal_code ~ '[ABCEGHJKLMNPRSTVXY]\d[ABCEGHJ-NPRSTV-Z][ ]?\d[ABCEGHJ-NPRSTV-Z]\d' ;
