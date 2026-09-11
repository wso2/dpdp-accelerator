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

CREATE TABLE IF NOT EXISTS DPDP_CONSENT_STATUS_AUDIT (
  AUDIT_ID        VARCHAR(36)  NOT NULL,
  CONSENT_ID      VARCHAR(255) NOT NULL,
  ORG_ID          VARCHAR(255) NOT NULL DEFAULT 'carbon.super',
  PREVIOUS_STATUS VARCHAR(64),
  CURRENT_STATUS  VARCHAR(64)  NOT NULL,
  ACTION_TYPE     VARCHAR(64)  NOT NULL,
  ACTION_BY       VARCHAR(255),
  ACTION_TIME     BIGINT       NOT NULL,
  PRIMARY KEY (AUDIT_ID),
  INDEX IDX_DPDP_STATUS_AUDIT_CONSENT_TIME (CONSENT_ID, ACTION_TIME),
  INDEX IDX_DPDP_STATUS_AUDIT_ORG (ORG_ID)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS DPDP_CONSENT_HISTORY (
  HISTORY_ID  VARCHAR(36)  NOT NULL,
  CONSENT_ID  VARCHAR(255) NOT NULL,
  ORG_ID      VARCHAR(255) NOT NULL DEFAULT 'carbon.super',
  ACTION_TYPE VARCHAR(64)  NOT NULL,
  SNAPSHOT    JSON,
  ACTION_BY   VARCHAR(255),
  ACTION_TIME BIGINT       NOT NULL,
  PRIMARY KEY (HISTORY_ID),
  INDEX IDX_DPDP_HISTORY_CONSENT_TIME (CONSENT_ID, ACTION_TIME),
  INDEX IDX_DPDP_HISTORY_ORG (ORG_ID)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS DPDP_CONSENT_EXPIRY_TRACKER (
  CONSENT_ID  VARCHAR(255) NOT NULL,
  ORG_ID      VARCHAR(255) NOT NULL DEFAULT 'carbon.super',
  EXPIRY_TIME BIGINT       NOT NULL,
  PRIMARY KEY (CONSENT_ID),
  INDEX IDX_DPDP_EXPIRY_TRACKER_TIME (EXPIRY_TIME)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
