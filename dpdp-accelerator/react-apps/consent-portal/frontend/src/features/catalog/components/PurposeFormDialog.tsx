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
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { CircleHelp, Plus, Trash2 } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type {
  ElementSummary,
  PurposeCreateRequest,
  PurposeElementRequest,
  PurposeVersion,
  PurposeVersionCreateRequest,
} from '../../../types/catalog'
import { useElementOptionsQuery } from '../hooks/useCatalogQueries'
import {
  entriesToProperties,
  propertiesToEntries,
  type PropertyEntry,
} from '../utils/formProperties'
import ElementVersionSelect from './ElementVersionSelect'
import PropertyEditor from './PropertyEditor'

interface PurposeElementFormRow extends PurposeElementRequest {
  id: number
  elementKey: string
}

type PurposeOwnership = 'organization' | 'group'

interface PurposeFormDialogProps {
  open: boolean
  initialValue: PurposeVersion | undefined
  organizationId: string
  loading: boolean
  error: string | undefined
  onClose: () => void
  onCreate: (payload: PurposeCreateRequest, groupId?: string) => void
  onCreateVersion: ((payload: PurposeVersionCreateRequest) => void) | undefined
}

function elementKey(element: Pick<ElementSummary, 'name' | 'namespace'>): string {
  return `${element.namespace}::${element.name}`
}

function elementLabel(element: ElementSummary): string {
  return `${element.displayName ?? element.name} (${element.namespace})`
}

function PurposeFormDialog({
  open,
  initialValue,
  organizationId,
  loading,
  error,
  onClose,
  onCreate,
  onCreateVersion,
}: PurposeFormDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const optionsQuery = useElementOptionsQuery(open)
  const [name, setName] = useState(initialValue?.name ?? '')
  const [ownership, setOwnership] = useState<PurposeOwnership>('organization')
  const [groupId, setGroupId] = useState('')
  const [displayName, setDisplayName] = useState(initialValue?.displayName ?? '')
  const [description, setDescription] = useState(initialValue?.description ?? '')
  const [properties, setProperties] = useState<PropertyEntry[]>(
    propertiesToEntries(initialValue?.properties),
  )
  const [elements, setElements] = useState<PurposeElementFormRow[]>(
    (initialValue?.elements ?? []).map((element, index) => ({
      id: index,
      elementKey: elementKey(element),
      name: element.name,
      namespace: element.namespace,
      version: element.version,
      mandatory: element.mandatory,
    })),
  )
  const [validationError, setValidationError] = useState('')
  const contentRef = useRef<HTMLDivElement>(null)
  const versionMode = Boolean(initialValue)
  const availableElements = optionsQuery.data?.data ?? []
  const requiredFieldsComplete =
    (versionMode || Boolean(name.trim())) &&
    (versionMode || ownership !== 'group' || Boolean(groupId.trim())) &&
    elements.length > 0 &&
    elements.every((element) => Boolean(element.name.trim()))
  const organizationWide = initialValue?.groupId === organizationId
  const immutablePurposeMessage = organizationWide
    ? t('catalog.messages.immutableOrganizationPurpose', { name: initialValue?.name })
    : t('catalog.messages.immutableGroupPurpose', {
        name: initialValue?.name,
        groupId: initialValue?.groupId,
      })

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
    if (!versionMode && ownership === 'group' && !groupId.trim()) {
      setValidationError(t('catalog.validation.groupIdRequired'))
      scrollToError()
      return
    }
    if (elements.length === 0 || elements.some((element) => !element.name)) {
      setValidationError(t('catalog.validation.elementRequired'))
      scrollToError()
      return
    }
    if (new Set(elements.map((element) => element.elementKey)).size !== elements.length) {
      setValidationError(t('catalog.validation.duplicateElements'))
      scrollToError()
      return
    }

    const propertyKeys = properties.map((property) => property.key.trim()).filter(Boolean)
    if (new Set(propertyKeys).size !== propertyKeys.length) {
      setValidationError(t('catalog.validation.duplicatePropertyKeys'))
      scrollToError()
      return
    }

    const purposeElements = elements.map(
      ({ name: elementName, namespace, version, mandatory }) => ({
        name: elementName,
        namespace: namespace || undefined,
        version: version || undefined,
        mandatory,
      }),
    )
    const versionPayload: PurposeVersionCreateRequest = {
      displayName: displayName.trim() || undefined,
      description: description.trim() || undefined,
      properties: entriesToProperties(properties),
      elements: purposeElements,
    }

    if (versionMode && onCreateVersion) {
      onCreateVersion(versionPayload)
      return
    }

    onCreate(
      { ...versionPayload, name: name.trim() },
      ownership === 'group' ? groupId.trim() : undefined,
    )
  }

  const addElementRow = (): void => {
    setElements([
      ...elements,
      {
        id: Date.now(),
        elementKey: '',
        name: '',
        namespace: '',
        version: '',
        mandatory: false,
      },
    ])
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
            {versionMode ? t('catalog.purposes.newVersion') : t('catalog.purposes.createTitle')}
          </Typography>
          {versionMode ? (
            <Tooltip arrow title={immutablePurposeMessage}>
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

          <Box>
            <Stack spacing={2}>
              <Typography variant="subtitle2" fontWeight={600}>
                {t('catalog.purposes.detailsSection')}
              </Typography>
              {!versionMode ? (
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <TextField
                    required
                    fullWidth
                    size="small"
                    label={t('catalog.fields.name')}
                    value={name}
                    slotProps={{ htmlInput: { maxLength: 255 } }}
                    onChange={(event) => setName(event.target.value)}
                  />
                  <TextField
                    select
                    required
                    fullWidth
                    size="small"
                    label={t('catalog.fields.purposeScope')}
                    value={ownership}
                    onChange={(event) => {
                      const nextOwnership = event.target.value as PurposeOwnership
                      setOwnership(nextOwnership)
                      if (nextOwnership === 'organization') setGroupId('')
                    }}
                  >
                    <MenuItem value="organization">{t('catalog.purposes.orgWide')}</MenuItem>
                    <MenuItem value="group">{t('catalog.values.specificGroup')}</MenuItem>
                  </TextField>
                </Stack>
              ) : null}
              {!versionMode && ownership === 'group' ? (
                <TextField
                  required
                  fullWidth
                  size="small"
                  label={t('catalog.fields.groupId')}
                  value={groupId}
                  onChange={(event) => setGroupId(event.target.value)}
                />
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
            </Stack>
          </Box>

          <Box>
            <PropertyEditor entries={properties} onChange={setProperties} />
          </Box>

          <Box>
            <Stack
              direction="row"
              alignItems="center"
              justifyContent="space-between"
              sx={{ mb: 1 }}
            >
              <Typography variant="subtitle2" fontWeight={600}>
                {t('catalog.fields.elements')} *
              </Typography>
              <Button
                size="small"
                startIcon={<Plus size={16} />}
                disabled={availableElements.length === 0}
                onClick={addElementRow}
              >
                {t('catalog.actions.addElement')}
              </Button>
            </Stack>

            <Stack spacing={1.25}>
              {optionsQuery.isLoading ? (
                <Typography variant="body2">{t('catalog.messages.loadingElements')}</Typography>
              ) : null}
              {!optionsQuery.isLoading && availableElements.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t('catalog.messages.createElementFirst')}
                </Typography>
              ) : null}
              {!optionsQuery.isLoading && availableElements.length > 0 && elements.length === 0 ? (
                <Box
                  sx={{
                    py: 3,
                    px: 2,
                    border: 1,
                    borderStyle: 'dashed',
                    borderColor: 'divider',
                    borderRadius: 1,
                    textAlign: 'center',
                  }}
                >
                  <Typography variant="body2" color="text.secondary">
                    {t('catalog.messages.noPurposeElements')}
                  </Typography>
                </Box>
              ) : null}
              {elements.map((row) => {
                const selectedElement =
                  availableElements.find((element) => elementKey(element) === row.elementKey) ??
                  null

                return (
                  <Box key={row.id} sx={{ py: 0.5 }}>
                    <Box
                      sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                          xs: '1fr',
                          sm: 'minmax(0, 1fr) 80px auto 36px',
                        },
                        gap: 1,
                        alignItems: 'center',
                      }}
                    >
                      <Autocomplete
                        size="small"
                        options={availableElements}
                        value={selectedElement}
                        getOptionLabel={elementLabel}
                        isOptionEqualToValue={(option, value) =>
                          option.elementId === value.elementId
                        }
                        getOptionDisabled={(option) =>
                          elements.some(
                            (item) => item.id !== row.id && item.elementKey === elementKey(option),
                          )
                        }
                        noOptionsText={t('catalog.messages.noElementsFound')}
                        onChange={(_, selected) => {
                          setElements(
                            elements.map((item) =>
                              item.id === row.id
                                ? {
                                    ...item,
                                    elementKey: selected ? elementKey(selected) : '',
                                    name: selected?.name ?? '',
                                    namespace: selected?.namespace ?? '',
                                    version: selected?.version ?? '',
                                  }
                                : item,
                            ),
                          )
                        }}
                        renderInput={(params) => (
                          // Oxygen UI Autocomplete requires forwarding its generated input props.
                          // eslint-disable-next-line react/jsx-props-no-spreading
                          <TextField {...params} required label={t('catalog.fields.element')} />
                        )}
                      />
                      <ElementVersionSelect
                        elementId={selectedElement?.elementId}
                        latestVersion={selectedElement?.version}
                        value={row.version ?? ''}
                        label={t('catalog.fields.version')}
                        allowAny={false}
                        onChange={(version) => {
                          setElements((currentElements) =>
                            currentElements.map((item) =>
                              item.id === row.id ? { ...item, version } : item,
                            ),
                          )
                        }}
                      />
                      <FormControlLabel
                        sx={{ m: 0, whiteSpace: 'nowrap' }}
                        control={
                          <Checkbox
                            checked={row.mandatory}
                            onChange={(event) => {
                              setElements(
                                elements.map((item) =>
                                  item.id === row.id
                                    ? { ...item, mandatory: event.target.checked }
                                    : item,
                                ),
                              )
                            }}
                          />
                        }
                        label={t('catalog.fields.mandatory')}
                      />
                      <IconButton
                        size="small"
                        aria-label={t('catalog.actions.removeElement')}
                        onClick={() => setElements(elements.filter((item) => item.id !== row.id))}
                      >
                        <Trash2 size={17} />
                      </IconButton>
                    </Box>
                  </Box>
                )
              })}
            </Stack>
          </Box>
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

export default PurposeFormDialog
