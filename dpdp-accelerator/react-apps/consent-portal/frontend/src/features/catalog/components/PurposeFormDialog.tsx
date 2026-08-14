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
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PurposeInput } from '../../../types/catalog'
import { hasPropertyIssues, toPropertiesRecord, type PropertyRow } from '../utils/propertyRows'
import PropertyEditor from './PropertyEditor'
import PurposeElementPicker, { type SelectedElement } from './PurposeElementPicker'

interface PurposeFormDialogProps {
  open: boolean
  loading: boolean
  error?: string
  typeSuggestions: string[]
  onClose: () => void
  onSubmit: (payload: PurposeInput) => void
}

interface TouchedFields {
  name: boolean
  type: boolean
  version: boolean
}

const UNTOUCHED: TouchedFields = { name: false, type: false, version: false }

function PurposeFormDialog({
  open,
  loading,
  error,
  typeSuggestions,
  onClose,
  onSubmit,
}: PurposeFormDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [name, setName] = useState('')
  const [type, setType] = useState('')
  const [version, setVersion] = useState('')
  const [description, setDescription] = useState('')
  const [elements, setElements] = useState<SelectedElement[]>([])
  const [properties, setProperties] = useState<PropertyRow[]>([])
  const [touched, setTouched] = useState<TouchedFields>(UNTOUCHED)

  // Reset the form when the dialog transitions to open, adjusted during
  // render rather than in an effect (React docs: "Adjusting some state
  // when a prop changes").
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setName('')
      setType('')
      setVersion('')
      setDescription('')
      setElements([])
      setProperties([])
      setTouched(UNTOUCHED)
    }
  }

  const nameError = touched.name && !name.trim()
  const typeError = touched.type && !type.trim()
  const versionError = touched.version && !version.trim()
  const propertyErrors = hasPropertyIssues(properties)

  const handleSubmit = (): void => {
    setTouched({ name: true, type: true, version: true })
    if (!name.trim() || !type.trim() || !version.trim() || propertyErrors) {
      return
    }
    onSubmit({
      name: name.trim(),
      type: type.trim(),
      version: version.trim(),
      description: description.trim() || undefined,
      elements:
        elements.length > 0 ? elements.map(({ id, mandatory }) => ({ id, mandatory })) : undefined,
      properties: toPropertiesRecord(properties),
    })
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        {t('catalog.purposeForm.title')}
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            autoFocus
            required
            fullWidth
            label={t('catalog.purposeForm.nameLabel')}
            error={nameError}
            helperText={nameError ? t('catalog.purposeForm.nameRequired') : undefined}
            value={name}
            disabled={loading}
            onChange={(event) => setName(event.target.value)}
            onBlur={() => setTouched((prev) => ({ ...prev, name: true }))}
          />

          <Autocomplete
            freeSolo
            options={typeSuggestions}
            inputValue={type}
            disabled={loading}
            onInputChange={(_event, newValue) => setType(newValue)}
            onBlur={() => setTouched((prev) => ({ ...prev, type: true }))}
            renderInput={(params) => (
              <TextField
                // eslint-disable-next-line react/jsx-props-no-spreading -- MUI's Autocomplete requires forwarding all of `params`
                {...params}
                required
                label={t('catalog.purposeForm.typeLabel')}
                error={typeError}
                helperText={
                  typeError
                    ? t('catalog.purposeForm.typeRequired')
                    : t('catalog.purposeForm.typeHelp')
                }
              />
            )}
          />

          <TextField
            required
            fullWidth
            label={t('catalog.purposeForm.versionLabel')}
            error={versionError}
            helperText={
              versionError
                ? t('catalog.purposeForm.versionRequired')
                : t('catalog.purposeForm.versionHelp')
            }
            value={version}
            disabled={loading}
            onChange={(event) => setVersion(event.target.value)}
            onBlur={() => setTouched((prev) => ({ ...prev, version: true }))}
          />

          <TextField
            fullWidth
            multiline
            minRows={2}
            label={t('catalog.elementForm.descriptionLabel')}
            value={description}
            disabled={loading}
            onChange={(event) => setDescription(event.target.value)}
          />

          <PurposeElementPicker selected={elements} disabled={loading} onChange={setElements} />

          <PropertyEditor rows={properties} disabled={loading} onChange={setProperties} />
        </Stack>
      </DialogContent>

      <DialogActions sx={{ p: 3, pt: 2, borderTop: 1, borderColor: 'divider' }}>
        <Button disabled={loading} onClick={onClose}>
          {t('catalog.actions.cancel')}
        </Button>
        <Button variant="contained" disabled={loading || propertyErrors} onClick={handleSubmit}>
          {loading ? t('catalog.purposeForm.submitting') : t('catalog.actions.create')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// React 19 no longer applies defaultProps on function components; this exists
// only to satisfy the react/require-default-props lint rule.
PurposeFormDialog.defaultProps = {
  error: undefined,
}

export default PurposeFormDialog
