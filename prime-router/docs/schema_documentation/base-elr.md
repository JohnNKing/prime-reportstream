
### Schema: base-elr
### Topic: elr
### Tracking Element: (message_id)
### Base On: none
### Extends: none
#### Description: A base ELR HL7 message. Used for ingesting data and supporting full ELR. This schema will 
not use any mappers. This is purely a pass-through schema. Child schemas are free to use
mappers, but this schema will treat all data coming in as valid.


---

**Name**: abnormal_flag

**ReportStream Internal Name**: abnormal_flag

**Type**: CODE

**PII**: No

**Format**: use value found in the Code column

**Cardinality**: [0..1]

**Value Sets**

Code | Display | System
---- | ------- | ------
A|Abnormal (applies to non-numeric results)|HL7
&#62;|Above absolute high-off instrument scale|HL7
H|Above high normal|HL7
HH|Above upper panic limits|HL7
AC|Anti-complementary substances present|HL7
<|Below absolute low-off instrument scale|HL7
L|Below low normal|HL7
LL|Below lower panic limits|HL7
B|Better--use when direction not relevant|HL7
TOX|Cytotoxic substance present|HL7
DET|Detected|HL7
IND|Indeterminate|HL7
I|Intermediate. Indicates for microbiology susceptibilities only.|HL7
MS|Moderately susceptible. Indicates for microbiology susceptibilities only.|HL7
NEG|Negative|HL7
null|No range defined, or normal ranges don't apply|HL7
NR|Non-reactive|HL7
N|Normal (applies to non-numeric results)|HL7
ND|Not Detected|HL7
POS|Positive|HL7
QCF|Quality Control Failure|HL7
RR|Reactive|HL7
R|Resistant. Indicates for microbiology susceptibilities only.|HL7
D|Significant change down|HL7
U|Significant change up|HL7
S|Susceptible. Indicates for microbiology susceptibilities only.|HL7
AA|Very abnormal (applies to non-numeric units, analogous to panic limits for numeric units)|HL7
VS|Very susceptible. Indicates for microbiology susceptibilities only.|HL7
WR|Weakly reactive|HL7
W|Worse--use when direction not relevant|HL7

**Documentation**:

This field is generated based on the normalcy status of the result. A = abnormal; N = normal


---

**Name**: comment

**ReportStream Internal Name**: comment

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: comment_source

**ReportStream Internal Name**: comment_source

**Type**: ID

**PII**: No

**Cardinality**: [0..1]

**Value Sets**

Code | Display | System
---- | ------- | ------
L|Ancillary (filler) department is source of comment|HL7
O|Other system is source of comment|HL7
P|Orderer (placer) is source of comment|HL7

---

**Name**: comment_type

**ReportStream Internal Name**: comment_type

**Type**: CODE

**PII**: No

**Format**: use value found in the Code column

**Cardinality**: [0..1]

**Value Sets**

Code | Display | System
---- | ------- | ------
1R|Primary Reason|HL7
2R|Secondary Reason|HL7
AI|Ancillary Instructions|HL7
DR|Duplicate/Interaction Reason|HL7
GI|General Instructions|HL7
GR|General Reason|HL7
PI|Patient Instructions|HL7
RE|Remark|HL7

---

**Name**: date_result_released

**ReportStream Internal Name**: date_result_released

**Type**: DATETIME

**PII**: No

**Cardinality**: [0..1]

---

**Name**: device_id

**ReportStream Internal Name**: device_id

**Type**: TEXT

**PII**: No

**HL7 Fields**

- [OBX-17-1](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBX.17.1)
- [OBX-17-9](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBX.17.9)

**Cardinality**: [0..1]

---

**Name**: device_id_type

**ReportStream Internal Name**: device_id_type

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: equipment_model_id

**ReportStream Internal Name**: equipment_model_id

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: equipment_model_id_type

**ReportStream Internal Name**: equipment_model_id_type

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: equipment_model_name

**ReportStream Internal Name**: equipment_model_name

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: file_created_date

**ReportStream Internal Name**: file_created_date

**Type**: DATETIME

**PII**: No

**Cardinality**: [0..1]

---

**Name**: filler_clia

**ReportStream Internal Name**: filler_clia

**Type**: ID_CLIA

**PII**: No

**Cardinality**: [0..1]

---

**Name**: filler_name

**ReportStream Internal Name**: filler_name

**Type**: TEXT

**PII**: No

**HL7 Fields**

- [OBR-3-2](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.3.2)
- [ORC-3-2](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.3.2)

**Cardinality**: [0..1]

---

**Name**: filler_order_id

**ReportStream Internal Name**: filler_order_id

**Type**: ID

**PII**: No

**HL7 Fields**

- [OBR-3-1](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.3.1)
- [ORC-3-1](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.3.1)
- [SPM-2-2](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/SPM.2.2)

**Cardinality**: [0..1]

**Documentation**:

Accession number

---

**Name**: order_test_date

**ReportStream Internal Name**: order_test_date

**Type**: DATETIME

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordered_test_code

**ReportStream Internal Name**: ordered_test_code

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordered_test_encoding_version

**ReportStream Internal Name**: ordered_test_encoding_version

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordered_test_name

**ReportStream Internal Name**: ordered_test_name

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordered_test_system_abbr

**ReportStream Internal Name**: ordered_test_system_abbr

**Type**: TEXT

**PII**: No

**Default Value**: LN

**Cardinality**: [0..1]

---

**Name**: ordering_facility_city

**ReportStream Internal Name**: ordering_facility_city

**Type**: CITY

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_country

**ReportStream Internal Name**: ordering_facility_country

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_county_code

**ReportStream Internal Name**: ordering_facility_county_code

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_email

**ReportStream Internal Name**: ordering_facility_email

**Type**: EMAIL

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_name

**ReportStream Internal Name**: ordering_facility_name

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_phone_number

**ReportStream Internal Name**: ordering_facility_phone_number

**Type**: TELEPHONE

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_state

**ReportStream Internal Name**: ordering_facility_state

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_street

**ReportStream Internal Name**: ordering_facility_street

**Type**: STREET

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_street2

**ReportStream Internal Name**: ordering_facility_street2

**Type**: STREET_OR_BLANK

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_facility_zip_code

**ReportStream Internal Name**: ordering_facility_zip_code

**Type**: POSTAL_CODE

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_provider_city

**ReportStream Internal Name**: ordering_provider_city

**Type**: CITY

**PII**: Yes

**Cardinality**: [0..1]

---

**Name**: ordering_provider_country

**ReportStream Internal Name**: ordering_provider_country

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_provider_county_code

**ReportStream Internal Name**: ordering_provider_county_code

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_provider_first_name

**ReportStream Internal Name**: ordering_provider_first_name

**Type**: PERSON_NAME

**PII**: No

**HL7 Fields**

- [OBR-16-3](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.16.3)
- [ORC-12-3](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.12.3)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_id

**ReportStream Internal Name**: ordering_provider_id

**Type**: ID_NPI

**PII**: No

**HL7 Fields**

- [OBR-16-1](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.16.1)
- [ORC-12-1](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.12.1)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_id_authority

**ReportStream Internal Name**: ordering_provider_id_authority

**Type**: HD

**PII**: No

**HL7 Fields**

- [OBR-16-9](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.16.9)
- [ORC-12-9](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.12.9)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_id_authority_type

**ReportStream Internal Name**: ordering_provider_id_authority_type

**Type**: TEXT

**PII**: No

**HL7 Fields**

- [OBR-16-13](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.16.13)
- [ORC-12-13](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.12.13)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_last_name

**ReportStream Internal Name**: ordering_provider_last_name

**Type**: PERSON_NAME

**PII**: No

**HL7 Fields**

- [OBR-16-2](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.16.2)
- [ORC-12-2](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.12.2)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_middle_name

**ReportStream Internal Name**: ordering_provider_middle_name

**Type**: PERSON_NAME

**PII**: No

**HL7 Fields**

- [OBR-16-4](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.16.4)
- [ORC-12-4](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.12.4)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_phone_number

**ReportStream Internal Name**: ordering_provider_phone_number

**Type**: TELEPHONE

**PII**: Yes

**HL7 Fields**

- [OBR-17](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/OBR.17)
- [ORC-14](https://hl7-definition.caristix.com/v2/HL7v2.5.1/Fields/ORC.14)

**Cardinality**: [0..1]

---

**Name**: ordering_provider_state

**ReportStream Internal Name**: ordering_provider_state

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---

**Name**: ordering_provider_street

**ReportStream Internal Name**: ordering_provider_street

**Type**: STREET

**PII**: Yes

**Cardinality**: [0..1]

---

**Name**: ordering_provider_street2

**ReportStream Internal Name**: ordering_provider_street2

**Type**: STREET_OR_BLANK

**PII**: Yes

**Cardinality**: [0..1]

---

**Name**: ordering_provider_zip_code

**ReportStream Internal Name**: ordering_provider_zip_code

**Type**: POSTAL_CODE

**PII**: No

**Cardinality**: [0..1]

---

**Name**: organization_name

**ReportStream Internal Name**: organization_name

**Type**: TEXT

**PII**: No

**Cardinality**: [0..1]

---
