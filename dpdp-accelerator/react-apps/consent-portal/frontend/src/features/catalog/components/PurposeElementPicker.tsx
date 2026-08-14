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

import {
  Autocomplete,
  Checkbox,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import type { PurposeElementInput } from '../../../types/catalog'
import { useElementsQuery } from '../hooks/useCatalogQueries'

export interface SelectedElement extends PurposeElementInput {
  name: string
  displayName?: string
}

interface PurposeElementPickerProps {
  selected: SelectedElement[]
  disabled: boolean
  onChange: (selected: SelectedElement[]) => void
}

/** Best-effort single-page fetch for the picker; not a true "list everything". */
const ELEMENT_PICKER_PAGE_SIZE = 200

/** Multi-select against the Elements catalog, with a per-selection Mandatory toggle. */
function PurposeElementPicker({
  selected,
  disabled,
  onChange,
}: PurposeElementPickerProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const elementsQuery = useElementsQuery({ limit: ELEMENT_PICKER_PAGE_SIZE })
  const options = elementsQuery.data?.Elements ?? []
  // A selection seeded from an existing version may not be on this page of
  // options (the query is still pending, or the catalog exceeds the page
  // size), so fall back to the metadata already carried on `selected`
  // rather than silently dropping it from the value and the next submit.
  const selectedOptions = selected.map(
    (item) =>
      options.find((option) => option.id === item.id) ?? {
        id: item.id,
        name: item.name,
        displayName: item.displayName,
      },
  )

  return (
    <Stack spacing={1.5}>
      <Autocomplete
        multiple
        disabled={disabled}
        loading={elementsQuery.isPending}
        options={options}
        value={selectedOptions}
        getOptionLabel={(option) => option.displayName ?? option.name}
        isOptionEqualToValue={(option, optionValue) => option.id === optionValue.id}
        onChange={(_event, newValue) => {
          onChange(
            newValue.map((option) => {
              const existing = selected.find((item) => item.id === option.id)
              return {
                id: option.id,
                name: option.name,
                displayName: option.displayName,
                mandatory: existing?.mandatory ?? false,
              }
            }),
          )
        }}
        renderInput={(params) => (
          // eslint-disable-next-line react/jsx-props-no-spreading -- MUI's Autocomplete requires forwarding all of `params`
          <TextField {...params} label={t('catalog.purposeForm.elementsLabel')} />
        )}
      />

      {selected.length > 0 ? (
        <Stack spacing={0.5}>
          {selected.map((item) => (
            <FormControlLabel
              key={item.id}
              control={
                <Checkbox
                  size="small"
                  checked={item.mandatory}
                  disabled={disabled}
                  onChange={(event) =>
                    onChange(
                      selected.map((row) =>
                        row.id === item.id ? { ...row, mandatory: event.target.checked } : row,
                      ),
                    )
                  }
                />
              }
              label={
                <Typography variant="body2">
                  {item.displayName ?? item.name} —{' '}
                  {item.mandatory ? t('catalog.values.mandatory') : t('catalog.values.optional')}
                </Typography>
              }
            />
          ))}
        </Stack>
      ) : null}
    </Stack>
  )
}

export default PurposeElementPicker
