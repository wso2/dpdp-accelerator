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

import { Button, IconButton, Stack, TextField, Typography } from '@wso2/oxygen-ui'
import { Plus, X } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { EMPTY_PROPERTY_ROW, getPropertyRowIssues, type PropertyRow } from '../utils/propertyRows'

interface PropertyEditorProps {
  rows: PropertyRow[]
  disabled: boolean
  onChange: (rows: PropertyRow[]) => void
}

/**
 * Key/value property editor shared by the Element and Purpose forms.
 * Validates in place -- a value without a key, or a key reused across rows,
 * is flagged immediately rather than silently dropped on submit.
 */
function PropertyEditor({ rows, disabled, onChange }: PropertyEditorProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const issues = getPropertyRowIssues(rows)

  const updateRow = (index: number, next: Partial<PropertyRow>): void => {
    onChange(rows.map((row, i) => (i === index ? { ...row, ...next } : row)))
  }

  const removeRow = (index: number): void => {
    onChange(rows.filter((_, i) => i !== index))
  }

  return (
    <Stack spacing={1.5}>
      <Typography variant="subtitle2" fontWeight={600}>
        {t('catalog.elementForm.propertiesLabel')}
      </Typography>

      {rows.map((row, index) => {
        const { duplicateKey, orphanedValue } = issues[index]
        let keyHelperText: string | undefined
        if (duplicateKey) {
          keyHelperText = t('catalog.elementForm.propertyDuplicateKey')
        } else if (orphanedValue) {
          keyHelperText = t('catalog.elementForm.propertyKeyRequired')
        }

        return (
          // eslint-disable-next-line react/no-array-index-key -- rows have no stable id until saved
          <Stack key={index} direction="row" spacing={1} alignItems="flex-start">
            <TextField
              size="small"
              fullWidth
              label={t('catalog.elementForm.propertyKeyLabel')}
              error={duplicateKey || orphanedValue}
              helperText={keyHelperText}
              value={row.key}
              disabled={disabled}
              onChange={(event) => updateRow(index, { key: event.target.value })}
            />
            <TextField
              size="small"
              fullWidth
              label={t('catalog.elementForm.propertyValueLabel')}
              value={row.value}
              disabled={disabled}
              onChange={(event) => updateRow(index, { value: event.target.value })}
            />
            <IconButton
              size="small"
              disabled={disabled}
              aria-label={t('catalog.elementForm.removeProperty')}
              onClick={() => removeRow(index)}
            >
              <X size={16} />
            </IconButton>
          </Stack>
        )
      })}

      <Button
        size="small"
        variant="outlined"
        startIcon={<Plus size={16} />}
        disabled={disabled}
        sx={{ alignSelf: 'flex-start' }}
        onClick={() => onChange([...rows, { ...EMPTY_PROPERTY_ROW }])}
      >
        {t('catalog.elementForm.addProperty')}
      </Button>
    </Stack>
  )
}

export default PropertyEditor
