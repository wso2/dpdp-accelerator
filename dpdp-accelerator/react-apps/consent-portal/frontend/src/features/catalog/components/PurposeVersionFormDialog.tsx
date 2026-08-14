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
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  TextField,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PurposeVersionInput } from '../../../types/catalog'
import {
  fromPropertiesRecord,
  hasPropertyIssues,
  toPropertiesRecord,
  type PropertyRow,
} from '../utils/propertyRows'
import PropertyEditor from './PropertyEditor'
import PurposeElementPicker, { type SelectedElement } from './PurposeElementPicker'

interface PurposeVersionFormDialogProps {
  open: boolean
  loading: boolean
  error?: string
  existingVersions: string[]
  /** The version this dialog copies its starting fields from -- IS does not inherit them itself. */
  source: {
    description?: string
    elements: SelectedElement[]
    properties?: Record<string, string>
  }
  onClose: () => void
  onSubmit: (payload: PurposeVersionInput) => void
}

function PurposeVersionFormDialog({
  open,
  loading,
  error,
  existingVersions,
  source,
  onClose,
  onSubmit,
}: PurposeVersionFormDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [version, setVersion] = useState('')
  const [setAsLatest, setSetAsLatest] = useState(true)
  const [description, setDescription] = useState(source.description ?? '')
  const [elements, setElements] = useState<SelectedElement[]>(source.elements)
  const [properties, setProperties] = useState<PropertyRow[]>(
    fromPropertiesRecord(source.properties),
  )
  const [versionTouched, setVersionTouched] = useState(false)

  // Reset (and re-copy the source version's fields) when the dialog opens,
  // adjusted during render rather than in an effect (React docs:
  // "Adjusting some state when a prop changes").
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setVersion('')
      setSetAsLatest(true)
      setDescription(source.description ?? '')
      setElements(source.elements)
      setProperties(fromPropertiesRecord(source.properties))
      setVersionTouched(false)
    }
  }

  const trimmedVersion = version.trim()
  const versionMissing = versionTouched && !trimmedVersion
  const versionDuplicate =
    versionTouched && Boolean(trimmedVersion) && existingVersions.includes(trimmedVersion)
  const propertyErrors = hasPropertyIssues(properties)

  let versionHelperText: string | undefined
  if (versionMissing) {
    versionHelperText = t('catalog.purposeVersionForm.versionRequired')
  } else if (versionDuplicate) {
    versionHelperText = t('catalog.purposeVersionForm.versionDuplicate')
  } else {
    versionHelperText = t('catalog.purposeVersionForm.versionHelp')
  }

  const handleSubmit = (): void => {
    setVersionTouched(true)
    if (!trimmedVersion || existingVersions.includes(trimmedVersion) || propertyErrors) {
      return
    }
    onSubmit({
      version: trimmedVersion,
      setAsLatest,
      description: description.trim() || undefined,
      elements:
        elements.length > 0 ? elements.map(({ id, mandatory }) => ({ id, mandatory })) : undefined,
      properties: toPropertiesRecord(properties),
    })
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        {t('catalog.purposeVersionForm.title')}
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            autoFocus
            required
            fullWidth
            label={t('catalog.purposeVersionForm.versionLabel')}
            error={versionMissing || versionDuplicate}
            helperText={versionHelperText}
            value={version}
            disabled={loading}
            onChange={(event) => setVersion(event.target.value)}
            onBlur={() => setVersionTouched(true)}
          />

          <FormControlLabel
            control={
              <Checkbox
                checked={setAsLatest}
                disabled={loading}
                onChange={(event) => setSetAsLatest(event.target.checked)}
              />
            }
            label={t('catalog.purposeVersionForm.setAsLatestLabel')}
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
          {loading ? t('catalog.purposeVersionForm.submitting') : t('catalog.actions.create')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// React 19 no longer applies defaultProps on function components; this exists
// only to satisfy the react/require-default-props lint rule.
PurposeVersionFormDialog.defaultProps = {
  error: undefined,
}

export default PurposeVersionFormDialog
