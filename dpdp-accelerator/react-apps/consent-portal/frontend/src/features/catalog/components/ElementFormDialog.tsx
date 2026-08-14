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
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Tooltip,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@wso2/oxygen-ui'
import { CircleHelp } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type {
  ElementCreateRequest,
  ElementType,
  ElementVersion,
  ElementVersionCreateRequest,
} from '../../../types/catalog'
import PropertyEditor from './PropertyEditor'
import {
  entriesToProperties,
  propertiesToEntries,
  type PropertyEntry,
} from '../utils/formProperties'
import { ELEMENT_TYPE_PRESENTATION } from '../utils/elementTypePresentation'

interface ElementFormDialogProps {
  open: boolean
  initialValue: ElementVersion | undefined
  loading: boolean
  error: string | undefined
  onClose: () => void
  onCreate: (payload: ElementCreateRequest) => void
  onCreateVersion: ((payload: ElementVersionCreateRequest) => void) | undefined
}

type NamespaceMode = 'default' | 'custom'

function ElementFormDialog({
  open,
  initialValue,
  loading,
  error,
  onClose,
  onCreate,
  onCreateVersion,
}: ElementFormDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [name, setName] = useState(initialValue?.name ?? '')
  const [namespace, setNamespace] = useState(initialValue?.namespace ?? '')
  const [namespaceMode, setNamespaceMode] = useState<NamespaceMode>(
    initialValue?.namespace ? 'custom' : 'default',
  )
  const [type, setType] = useState<ElementType>(initialValue?.type ?? 'basic')
  const [displayName, setDisplayName] = useState(initialValue?.displayName ?? '')
  const [description, setDescription] = useState(initialValue?.description ?? '')
  const [schema, setSchema] = useState(initialValue?.schema ?? '')
  const [properties, setProperties] = useState<PropertyEntry[]>(
    propertiesToEntries(initialValue?.properties),
  )
  const [validationError, setValidationError] = useState('')
  const contentRef = useRef<HTMLDivElement>(null)
  const versionMode = Boolean(initialValue)
  const requiredFieldsComplete =
    (versionMode || Boolean(name.trim())) &&
    (versionMode || namespaceMode === 'default' || Boolean(namespace.trim())) &&
    (type === 'basic' || Boolean(schema.trim()))

  const scrollToError = (): void => {
    if (contentRef.current) {
      contentRef.current.scrollTop = 0
    }
  }

  useEffect(() => {
    if (error) {
      scrollToError()
    }
  }, [error])

  const handleSubmit = (): void => {
    setValidationError('')

    if (!versionMode && !name.trim()) {
      setValidationError(t('catalog.validation.nameRequired'))
      scrollToError()
      return
    }
    if (!versionMode && namespaceMode === 'custom' && !namespace.trim()) {
      setValidationError(t('catalog.validation.namespaceRequired'))
      scrollToError()
      return
    }
    if ((type === 'json' || type === 'xml') && !schema.trim()) {
      setValidationError(t('catalog.validation.schemaRequired'))
      scrollToError()
      return
    }

    const versionPayload: ElementVersionCreateRequest = {
      displayName: displayName.trim() || undefined,
      description: description.trim() || undefined,
      schema: schema.trim() || undefined,
      properties: entriesToProperties(properties),
    }

    if (versionMode && onCreateVersion) {
      onCreateVersion(versionPayload)
      return
    }

    onCreate({
      ...versionPayload,
      name: name.trim(),
      namespace: namespaceMode === 'custom' ? namespace.trim() || undefined : undefined,
      type,
    })
  }

  return (
    <Dialog
      open={open}
      onClose={loading ? undefined : onClose}
      maxWidth={false}
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          width: 'calc(100% - 32px)',
          maxWidth: 720,
          maxHeight: 'calc(100vh - 48px)',
          borderRadius: 1,
          ...theme.applyStyles('light', { bgcolor: theme.palette.grey[50] }),
          ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.06)' }),
        }),
      }}
    >
      <DialogTitle
        sx={{
          p: 3,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
        }}
      >
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Typography variant="h4" fontWeight={700}>
            {versionMode ? t('catalog.elements.newVersion') : t('catalog.elements.createTitle')}
          </Typography>
          {versionMode ? (
            <Tooltip
              arrow
              title={t('catalog.messages.immutableElement', {
                name: initialValue?.name,
                namespace: initialValue?.namespace,
                type: initialValue?.type,
              })}
            >
              <Box
                component="span"
                sx={{ display: 'inline-flex', alignItems: 'center', color: 'text.disabled' }}
              >
                <CircleHelp size={16} />
              </Box>
            </Tooltip>
          ) : null}
        </Stack>
      </DialogTitle>
      <DialogContent ref={contentRef} sx={{ px: 3, mt: 3, pb: 3, overflowY: 'auto' }}>
        <Stack spacing={2}>
          {validationError || error ? (
            <Alert severity="error">{validationError || error}</Alert>
          ) : null}

          <Typography variant="subtitle2" fontWeight={600}>
            {t('catalog.elements.detailsSection')}
          </Typography>

          {!versionMode ? (
            <Stack spacing={2}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={2}
                alignItems={{ sm: 'flex-start' }}
              >
                <TextField
                  required
                  fullWidth
                  size="small"
                  label={t('catalog.fields.name')}
                  value={name}
                  slotProps={{ htmlInput: { maxLength: 255 } }}
                  onChange={(event) => setName(event.target.value)}
                />
                <ToggleButtonGroup
                  exclusive
                  color="primary"
                  size="small"
                  value={type}
                  aria-label={t('catalog.fields.type')}
                  sx={{
                    flexShrink: 0,
                    '& .MuiToggleButton-root': { textTransform: 'none' },
                  }}
                  onChange={(_, nextType: ElementType | null) => {
                    if (nextType) setType(nextType)
                  }}
                >
                  {(['basic', 'json', 'xml'] as const).map((elementType) => {
                    const { Icon, label } = ELEMENT_TYPE_PRESENTATION[elementType]

                    return (
                      <ToggleButton key={elementType} value={elementType}>
                        <Stack direction="row" spacing={0.75} alignItems="center">
                          <Icon size={14} />
                          <Box component="span">{label}</Box>
                        </Stack>
                      </ToggleButton>
                    )
                  })}
                </ToggleButtonGroup>
              </Stack>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                alignItems={{ sm: 'center' }}
              >
                <ToggleButtonGroup
                  exclusive
                  color="primary"
                  size="small"
                  value={namespaceMode}
                  aria-label={t('catalog.fields.namespace')}
                  sx={{ '& .MuiToggleButton-root': { textTransform: 'none' } }}
                  onChange={(_, nextMode: NamespaceMode | null) => {
                    if (!nextMode) return
                    setNamespaceMode(nextMode)
                    if (nextMode === 'default') setNamespace('')
                  }}
                >
                  <ToggleButton value="default">
                    {t('catalog.values.defaultNamespace')}
                  </ToggleButton>
                  <ToggleButton value="custom">{t('catalog.values.customNamespace')}</ToggleButton>
                </ToggleButtonGroup>
                {namespaceMode === 'custom' ? (
                  <TextField
                    required
                    size="small"
                    label={t('catalog.fields.namespace')}
                    value={namespace}
                    slotProps={{ htmlInput: { maxLength: 255 } }}
                    onChange={(event) => setNamespace(event.target.value)}
                    sx={{ width: { xs: '100%', sm: 220 } }}
                  />
                ) : null}
              </Stack>
            </Stack>
          ) : null}
          <TextField
            fullWidth
            size="small"
            label={t('catalog.fields.displayName')}
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
          <TextField
            fullWidth
            size="small"
            multiline
            minRows={2}
            label={t('catalog.fields.description')}
            value={description}
            slotProps={{ htmlInput: { maxLength: 1024 } }}
            onChange={(event) => setDescription(event.target.value)}
          />
          {type !== 'basic' ? (
            <TextField
              required
              fullWidth
              size="small"
              multiline
              minRows={5}
              label={t('catalog.fields.schema')}
              value={schema}
              onChange={(event) => setSchema(event.target.value)}
            />
          ) : null}
          <PropertyEditor entries={properties} embedded={false} onChange={setProperties} />
        </Stack>
      </DialogContent>
      <DialogActions
        sx={{
          px: 3,
          py: 2.5,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
          flexDirection: { xs: 'column-reverse', sm: 'row' },
          gap: 1.25,
        }}
      >
        <Button fullWidth variant="outlined" onClick={onClose} disabled={loading}>
          {t('catalog.actions.cancel')}
        </Button>
        <Button
          fullWidth
          variant="contained"
          onClick={handleSubmit}
          disabled={loading || !requiredFieldsComplete}
        >
          {loading ? t('catalog.actions.saving') : t('catalog.actions.create')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ElementFormDialog
