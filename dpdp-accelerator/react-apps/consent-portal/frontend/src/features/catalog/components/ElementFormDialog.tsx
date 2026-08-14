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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ElementInput } from '../../../types/catalog'
import { hasPropertyIssues, toPropertiesRecord, type PropertyRow } from '../utils/propertyRows'
import PropertyEditor from './PropertyEditor'

interface ElementFormDialogProps {
  open: boolean
  loading: boolean
  error?: string
  onClose: () => void
  onSubmit: (payload: ElementInput) => void
}

function ElementFormDialog({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: ElementFormDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [name, setName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [description, setDescription] = useState('')
  const [properties, setProperties] = useState<PropertyRow[]>([])
  const [nameTouched, setNameTouched] = useState(false)
  const nameError = nameTouched && !name.trim()

  // Reset the form fields when the dialog transitions to open, adjusted
  // during render rather than in an effect (React docs: "Adjusting some
  // state when a prop changes").
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setName('')
      setDisplayName('')
      setDescription('')
      setProperties([])
      setNameTouched(false)
    }
  }

  const propertyErrors = hasPropertyIssues(properties)

  const handleSubmit = (): void => {
    setNameTouched(true)
    if (!name.trim() || propertyErrors) {
      return
    }
    onSubmit({
      name: name.trim(),
      displayName: displayName.trim() || undefined,
      description: description.trim() || undefined,
      properties: toPropertiesRecord(properties),
    })
  }

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        {t('catalog.elementForm.title')}
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            autoFocus
            required
            fullWidth
            label={t('catalog.elementForm.nameLabel')}
            helperText={
              nameError ? t('catalog.elementForm.nameRequired') : t('catalog.elementForm.nameHelp')
            }
            error={nameError}
            value={name}
            disabled={loading}
            onChange={(event) => setName(event.target.value)}
            onBlur={() => setNameTouched(true)}
          />

          <TextField
            fullWidth
            label={t('catalog.elementForm.displayNameLabel')}
            value={displayName}
            disabled={loading}
            onChange={(event) => setDisplayName(event.target.value)}
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

          <PropertyEditor rows={properties} disabled={loading} onChange={setProperties} />
        </Stack>
      </DialogContent>

      <DialogActions sx={{ p: 3, pt: 2, borderTop: 1, borderColor: 'divider' }}>
        <Button disabled={loading} onClick={onClose}>
          {t('catalog.actions.cancel')}
        </Button>
        <Button variant="contained" disabled={loading || propertyErrors} onClick={handleSubmit}>
          {loading ? t('catalog.elementForm.submitting') : t('catalog.actions.create')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// React 19 no longer applies defaultProps on function components; this exists
// only to satisfy the react/require-default-props lint rule.
ElementFormDialog.defaultProps = {
  error: undefined,
}

export default ElementFormDialog
