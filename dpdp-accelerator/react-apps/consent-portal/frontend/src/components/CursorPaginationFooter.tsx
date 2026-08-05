/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { Button, MenuItem, Stack, TextField } from '@wso2/oxygen-ui'
import { ChevronLeft, ChevronRight } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'

interface CursorPaginationFooterProps {
  rowsPerPage: number
  rowsPerPageOptions: readonly number[]
  hasPreviousPage: boolean
  hasNextPage: boolean
  disabled?: boolean
  onRowsPerPageChange: (rowsPerPage: number) => void
  onPreviousPage: () => void
  onNextPage: () => void
}

/**
 * Previous/next pagination footer.
 *
 * The Identity Server APIs are cursor based and report no reliable grand
 * total, so the portal never renders numbered pages or an exact record count.
 */
function CursorPaginationFooter({
  rowsPerPage,
  rowsPerPageOptions,
  hasPreviousPage,
  hasNextPage,
  disabled = false,
  onRowsPerPageChange,
  onPreviousPage,
  onNextPage,
}: CursorPaginationFooterProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Stack
      direction="row"
      spacing={2}
      alignItems="center"
      justifyContent="flex-end"
      sx={{ px: 2, py: 1.5, borderTop: 1, borderColor: 'divider' }}
    >
      <TextField
        select
        size="small"
        value={rowsPerPage}
        label={t('pagination.rowsPerPage')}
        sx={{ width: 140 }}
        onChange={(event) => onRowsPerPageChange(Number(event.target.value))}
      >
        {rowsPerPageOptions.map((option) => (
          <MenuItem key={option} value={option}>
            {option}
          </MenuItem>
        ))}
      </TextField>
      <Button
        size="small"
        variant="outlined"
        startIcon={<ChevronLeft size={16} />}
        disabled={!hasPreviousPage || disabled}
        onClick={onPreviousPage}
      >
        {t('pagination.previous')}
      </Button>
      <Button
        size="small"
        variant="outlined"
        endIcon={<ChevronRight size={16} />}
        disabled={!hasNextPage || disabled}
        onClick={onNextPage}
      >
        {t('pagination.next')}
      </Button>
    </Stack>
  )
}

CursorPaginationFooter.defaultProps = {
  disabled: false,
}

export default CursorPaginationFooter
