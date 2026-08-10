-- Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
--
-- WSO2 LLC. licenses this file to you under the Apache License,
-- Version 2.0 (the "License"); you may not use this file except
-- in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.

-- Database DDL for Complaint Server (MySQL)
-- Derived from complaint-redressal-erd, with one addition:
--   COMPLAINT_ATTACHMENT.FILE_CONTENT_TYPE - the ERD has no column for this, but the API response
--   requires a contentType per attachment and it cannot be reliably derived from the binary data alone.
--   SIZE_BYTES is NOT stored as a column; it is derived at query time via LENGTH(FILE_DATA).

-- COMPLAINT definition
CREATE TABLE IF NOT EXISTS `COMPLAINT` (
  `COMPLAINT_ID` char(36) NOT NULL,
  `ORG_ID` varchar(255) NOT NULL,
  `USER_ID` varchar(255) NOT NULL,
  `REFERENCE_ID` varchar(32) NOT NULL,
  `CATEGORY` varchar(64) NOT NULL,
  `PRIORITY` varchar(16) NOT NULL,
  `STATUS` varchar(32) NOT NULL DEFAULT 'OPEN',
  `DESCRIPTION` text NOT NULL,
  `CREATED_TIME` bigint NOT NULL,
  `UPDATED_TIME` bigint NOT NULL,
  `STATUTORY_DUE_TIME` bigint NOT NULL,
  PRIMARY KEY (`COMPLAINT_ID`, `ORG_ID`),
  UNIQUE KEY `UQ_COMPLAINT_REFERENCE` (`ORG_ID`, `REFERENCE_ID`),
  KEY `IDX_COMPLAINT_ORG_STATUS` (`ORG_ID`, `STATUS`),
  KEY `IDX_COMPLAINT_ORG_USER` (`ORG_ID`, `USER_ID`),
  -- Primary enforcement is the ComplaintPriority/ComplaintStatus enums at the app layer (see
  -- StatusTransitionValidator, PriorityMapper) - adding a new value there is a code change, not a
  -- DB migration. The CHECK here is only a backstop against direct writes that bypass that layer.
  CONSTRAINT `CHK_COMPLAINT_PRIORITY` CHECK (`PRIORITY` in ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
  CONSTRAINT `CHK_COMPLAINT_STATUS` CHECK (`STATUS` in
      ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CLIENT', 'AWAITING_INTERNAL_REVIEW', 'RESOLVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- COMPLAINT_EVENT definition (timeline: status changes, comments, internal notes)
CREATE TABLE IF NOT EXISTS `COMPLAINT_EVENT` (
  `COMPLAINT_EVENT_ID` char(36) NOT NULL,
  `ORG_ID` varchar(255) NOT NULL,
  `COMPLAINT_ID` char(36) NOT NULL,
  `ACTOR_USER_ID` varchar(255) DEFAULT NULL,
  `ACTOR_ROLE` varchar(32) NOT NULL,
  `IS_PUBLIC` boolean NOT NULL DEFAULT TRUE,
  `COMMENT` text DEFAULT NULL,
  `FROM_STATUS` varchar(32) DEFAULT NULL,
  `TO_STATUS` varchar(32) DEFAULT NULL,
  `ACTION_TIME` bigint NOT NULL,
  PRIMARY KEY (`COMPLAINT_EVENT_ID`, `ORG_ID`),
  KEY `IDX_CE_COMPLAINT_TIME` (`COMPLAINT_ID`, `ORG_ID`, `ACTION_TIME`),
  CONSTRAINT `FK_CE_COMPLAINT` FOREIGN KEY (`COMPLAINT_ID`, `ORG_ID`) REFERENCES `COMPLAINT` (`COMPLAINT_ID`, `ORG_ID`),
  -- Primary enforcement is the ComplaintActorRole enum in ComplaintEventServiceImpl; this CHECK
  -- is only a backstop against direct writes (migrations, manual fixes) that bypass that layer.
  CONSTRAINT `CHK_CE_ACTOR_ROLE` CHECK (`ACTOR_ROLE` in ('USER', 'COMPLAINT_OFFICER', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- COMPLAINT_ATTACHMENT definition
CREATE TABLE IF NOT EXISTS `COMPLAINT_ATTACHMENT` (
  `ATTACHMENT_ID` char(36) NOT NULL,
  `ORG_ID` varchar(255) NOT NULL,
  `COMPLAINT_ID` char(36) NOT NULL,
  `COMPLAINT_EVENT_ID` char(36) DEFAULT NULL,
  `FILE_NAME` varchar(255) NOT NULL,
  `FILE_CONTENT_TYPE` varchar(128) NOT NULL,
  `FILE_DATA` longblob NOT NULL,
  `CREATED_TIME` bigint NOT NULL,
  PRIMARY KEY (`ATTACHMENT_ID`, `ORG_ID`),
  KEY `IDX_CA_COMPLAINT` (`COMPLAINT_ID`, `ORG_ID`),
  KEY `IDX_CA_EVENT` (`COMPLAINT_EVENT_ID`, `ORG_ID`),
  CONSTRAINT `FK_CA_COMPLAINT` FOREIGN KEY (`COMPLAINT_ID`, `ORG_ID`) REFERENCES `COMPLAINT` (`COMPLAINT_ID`, `ORG_ID`),
  CONSTRAINT `FK_CA_EVENT` FOREIGN KEY (`COMPLAINT_EVENT_ID`, `ORG_ID`)
      REFERENCES `COMPLAINT_EVENT` (`COMPLAINT_EVENT_ID`, `ORG_ID`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
