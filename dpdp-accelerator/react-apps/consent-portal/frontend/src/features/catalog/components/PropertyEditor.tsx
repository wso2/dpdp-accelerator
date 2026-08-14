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

import { Box, Button, IconButton, Stack, TextField, Typography } from '@wso2/oxygen-ui'
import { Plus, Trash2 } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type { PropertyEntry } from '../utils/formProperties'

interface PropertyEditorProps {
  entries: PropertyEntry[]
  embedded?: boolean
  onChange: (entries: PropertyEntry[]) => void
}

function PropertyEditor({ entries, embedded, onChange }: PropertyEditorProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const isEmbedded = embedded ?? false

  return (
    <Stack spacing={1}>
      <Stack
        direction="row"
        alignItems="center"
        justifyContent={isEmbedded ? 'flex-end' : 'space-between'}
      >
        {!isEmbedded ? (
          <Typography variant="subtitle2" fontWeight={600}>
            {t('catalog.fields.properties')}
          </Typography>
        ) : null}
        <Button
          size="small"
          startIcon={<Plus size={16} />}
          onClick={() => {
            onChange([...entries, { id: Date.now(), key: '', value: '' }])
          }}
        >
          {t('catalog.actions.addProperty')}
        </Button>
      </Stack>
      {entries.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('catalog.messages.noProperties')}
        </Typography>
      ) : null}
      {entries.map((entry) => (
        <Stack key={entry.id} direction="row" spacing={1} alignItems="center">
          <TextField
            size="small"
            label={t('catalog.fields.propertyKey')}
            value={entry.key}
            onChange={(event) => {
              onChange(
                entries.map((item) =>
                  item.id === entry.id ? { ...item, key: event.target.value } : item,
                ),
              )
            }}
            fullWidth
          />
          <TextField
            size="small"
            label={t('catalog.fields.propertyValue')}
            value={entry.value}
            onChange={(event) => {
              onChange(
                entries.map((item) =>
                  item.id === entry.id ? { ...item, value: event.target.value } : item,
                ),
              )
            }}
            fullWidth
          />
          <Box>
            <IconButton
              aria-label={t('catalog.actions.removeProperty')}
              onClick={() => {
                onChange(entries.filter((item) => item.id !== entry.id))
              }}
            >
              <Trash2 size={17} />
            </IconButton>
          </Box>
        </Stack>
      ))}
    </Stack>
  )
}

PropertyEditor.defaultProps = {
  embedded: false,
}

export default PropertyEditor
