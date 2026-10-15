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
  Box,
  Divider,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Fragment, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PurposeElementInput } from '../../../types/catalog'
import { buildElementNameFilter } from '../api/catalogApi'
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

/**
 * The Identity Server caps paginated results at 100 regardless of the limit
 * requested, so a static fetch can never surface every element once the
 * catalog grows past that - see
 * https://github.com/wso2/dpdp-accelerator/issues/7. Typing into the picker
 * now searches server-side via `buildElementNameFilter`, the same filter the
 * Elements list page itself uses, instead of filtering this one page client-side.
 */
const ELEMENT_PICKER_PAGE_SIZE = 100
const ELEMENT_SEARCH_DEBOUNCE_MS = 300

/** Multi-select against the Elements catalog, with a per-selection Mandatory switch. */
function PurposeElementPicker({
  selected,
  disabled,
  onChange,
}: PurposeElementPickerProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [inputValue, setInputValue] = useState('')
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(inputValue), ELEMENT_SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [inputValue])

  const elementsQuery = useElementsQuery({
    limit: ELEMENT_PICKER_PAGE_SIZE,
    filter: buildElementNameFilter(searchTerm),
  })
  const options = elementsQuery.data?.Elements ?? []
  // A selection seeded from an existing version may not be on this page of
  // options (the query is still pending, or the catalog exceeds the page
  // size), so fall back to the metadata already carried on `selected`
  // rather than silently dropping it from the value and the next submit.
  //
  // Memoized so this array keeps the same reference across renders that
  // don't actually change the selection - otherwise Autocomplete sees a
  // "new" value on every keystroke (searchTerm/options changing re-renders
  // this component) and resets its typed input text back to empty, making
  // the field appear untypable.
  const selectedOptions = useMemo(
    () =>
      selected.map(
        (item) =>
          options.find((option) => option.id === item.id) ?? {
            id: item.id,
            name: item.name,
            displayName: item.displayName,
          },
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-deriving on every `options` page change would recreate this on every keystroke, defeating the memoization
    [selected],
  )

  return (
    <Stack spacing={1.5}>
      <Autocomplete
        multiple
        disabled={disabled}
        loading={elementsQuery.isPending}
        options={options}
        value={selectedOptions}
        inputValue={inputValue}
        onInputChange={(_event, newInputValue) => setInputValue(newInputValue)}
        // The options list is already name-filtered server-side (see
        // useElementsQuery above); re-filtering client-side here would just
        // hide results whose display name doesn't share the typed substring
        // even though the server matched on the underlying name.
        filterOptions={(currentOptions) => currentOptions}
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
          <TextField {...params} label={t('catalog.purposes.form.elementsLabel')} />
        )}
      />

      {selected.length > 0 ? (
        <Box
          sx={{
            // Mirrors the theme's MuiOutlinedInput surface; a Paper would add the
            // theme's shadow and blur and read as a separate card.
            bgcolor: 'background.acrylic',
            border: 1,
            borderColor: 'divider',
            borderRadius: '8px',
          }}
        >
          {selected.map((item, index) => {
            const label = item.displayName ?? item.name
            return (
              <Fragment key={item.id}>
                {index > 0 ? <Divider /> : null}
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 2,
                    px: 2,
                    py: 1,
                  }}
                >
                  <Typography variant="body2" noWrap title={label} sx={{ minWidth: 0 }}>
                    {label}
                  </Typography>
                  <FormControlLabel
                    label={t('catalog.values.mandatory')}
                    sx={{ mr: 0, flexShrink: 0 }}
                    control={
                      <Switch
                        checked={item.mandatory}
                        disabled={disabled}
                        slotProps={{
                          input: {
                            'aria-label': t('catalog.purposes.form.elementRequirementLabel', {
                              name: label,
                            }),
                          },
                        }}
                        onChange={(event) =>
                          onChange(
                            selected.map((row) =>
                              row.id === item.id
                                ? { ...row, mandatory: event.target.checked }
                                : row,
                            ),
                          )
                        }
                      />
                    }
                  />
                </Box>
              </Fragment>
            )
          })}
        </Box>
      ) : null}
    </Stack>
  )
}

export default PurposeElementPicker
