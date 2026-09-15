/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.dao.queries;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintDBColumns;

import java.util.ArrayList;
import java.util.List;

/** Helper builder for constructing the dynamic COMPLAINT search/list and count queries. */
public class ComplaintQueryBuilder {

    private final String orgId;
    private final ComplaintCommonDBQueries queries;
    private String status;
    private String priority;
    private String userId;
    private String search;
    private String sort;

    public ComplaintQueryBuilder(String orgId, ComplaintCommonDBQueries queries) {
        this.orgId = orgId;
        this.queries = queries;
    }

    public ComplaintQueryBuilder setStatus(String status) {
        this.status = status;
        return this;
    }

    public ComplaintQueryBuilder setPriority(String priority) {
        this.priority = priority;
        return this;
    }

    public ComplaintQueryBuilder setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public ComplaintQueryBuilder setSearch(String search) {
        this.search = search;
        return this;
    }

    public ComplaintQueryBuilder setSort(String sort) {
        this.sort = sort;
        return this;
    }

    public QueryResult buildSelectQuery(int limit, int offset) {
        StringBuilder sql = new StringBuilder(queries.getListComplaintsBaseQuery());
        List<Object> params = buildWhereClauseAndParams(sql);
        sql.append("ORDER BY ").append(resolveSortColumn()).append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return new QueryResult(sql.toString(), params);
    }

    public QueryResult buildCountQuery() {
        StringBuilder sql = new StringBuilder(queries.getCountComplaintsBaseQuery());
        List<Object> params = buildWhereClauseAndParams(sql);
        return new QueryResult(sql.toString(), params);
    }

    private List<Object> buildWhereClauseAndParams(StringBuilder sql) {
        List<Object> params = new ArrayList<>();
        params.add(orgId);

        if (status != null && !status.trim().isEmpty()) {
            sql.append("AND ").append(ComplaintDBColumns.STATUS).append(" = ? ");
            params.add(status.trim());
        }
        if (priority != null && !priority.trim().isEmpty()) {
            sql.append("AND ").append(ComplaintDBColumns.PRIORITY).append(" = ? ");
            params.add(priority.trim());
        }
        if (userId != null && !userId.trim().isEmpty()) {
            sql.append("AND ").append(ComplaintDBColumns.USER_ID).append(" = ? ");
            params.add(userId.trim());
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (")
                    .append(QueryBuilderUtils.buildEscapedLikePredicate(
                            "LOWER(" + ComplaintDBColumns.REFERENCE_ID + ")"))
                    .append(" OR ").append(QueryBuilderUtils.buildEscapedLikePredicate(
                            "LOWER(" + ComplaintDBColumns.USER_NAME + ")"))
                    .append(") ");
            String term = QueryBuilderUtils.buildCaseInsensitiveContainsPattern(search);
            params.add(term);
            params.add(term);
        }
        return params;
    }

    /**
     * Only updatedTime / submittedTime(createdTime) / statutoryDueTime are sortable, "-" prefix =
     * descending.
     */
    private String resolveSortColumn() {
        String orderBy = ComplaintDBColumns.UPDATED_TIME + " DESC";
        if (sort != null && !sort.trim().isEmpty()) {
            String s = sort.trim();
            boolean desc = s.startsWith("-");
            String field = desc ? s.substring(1) : s;
            String column;
            switch (field) {
                case "updatedTime":
                    column = ComplaintDBColumns.UPDATED_TIME;
                    break;
                case "submittedTime":
                    column = ComplaintDBColumns.CREATED_TIME;
                    break;
                case "statutoryDueTime":
                    column = ComplaintDBColumns.STATUTORY_DUE_TIME;
                    break;
                default:
                    column = ComplaintDBColumns.UPDATED_TIME;
            }
            orderBy = column + (desc ? " DESC" : " ASC");
        }
        return orderBy;
    }
}
