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
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { Key, X, RefreshCw } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type {
  DeliveryMode,
  PurposeFilterMode,
  SubscriptionInput,
} from '../../../types/subscription'
import { DELIVERY_MODES, PURPOSE_FILTER_MODES } from '../../../types/subscription'
import SubscriptionTopicPicker from './SubscriptionTopicPicker'
import { supportsConsentPurposeFilter } from '../utils/topicCapabilities'
import { MAX_SUBSCRIPTION_TOPICS } from '../constants'
import SubscriptionPurposePicker from './SubscriptionPurposePicker'

interface SubscriptionRegisterDialogProps {
  open: boolean
  loading: boolean
  error?: string
  onClose: () => void
  onSubmit: (payload: SubscriptionInput) => void
}

function generateRandomHexSecret(length = 32): string {
  const bytes = new Uint8Array(length / 2)
  window.crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export default function SubscriptionRegisterDialog({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: SubscriptionRegisterDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const [selectionBusy, setSelectionBusy] = useState(false)

  const [name, setName] = useState('')
  const [selectedTopics, setSelectedTopics] = useState<string[]>([])
  const [filterMode, setFilterMode] = useState<PurposeFilterMode>('all')
  const [selectedPurposes, setSelectedPurposes] = useState<string[]>([])
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>('webhook')
  const [callbackUrl, setCallbackUrl] = useState('')
  const [sharedSecret, setSharedSecret] = useState('')

  const [nameError, setNameError] = useState('')
  const [topicError, setTopicError] = useState('')
  const [callbackUrlError, setCallbackUrlError] = useState('')
  const [purposesError, setPurposesError] = useState('')
  const [secretError, setSecretError] = useState('')

  useEffect(() => {
    if (open) {
      setSelectedPurposes([])
      setPurposesError('')
      setNameError('')
      setTopicError('')
      setCallbackUrlError('')
      setSecretError('')
      setSharedSecret('')
    }
  }, [open])
  const handleGenerateSecret = (): void => {
    setSharedSecret(generateRandomHexSecret())
    if (secretError) setSecretError('')
  }

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()
    if (selectionBusy || loading) return

    let hasError = false

    const trimmedName = name.trim()
    if (!trimmedName) {
      setNameError(t('subscriptions.dialog.nameRequired', 'Subscription name is required.'))
      hasError = true
    } else if (trimmedName.length > 225) {
      setNameError(
        t('subscriptions.dialog.nameTooLong', 'Subscription name must not exceed 225 characters.'),
      )
      hasError = true
    } else {
      setNameError('')
    }

    if (selectedTopics.length === 0 || selectedTopics.length > MAX_SUBSCRIPTION_TOPICS) {
      setTopicError(t('subscriptions.dialog.topicRequired', 'Topic is required.'))
      hasError = true
    } else {
      setTopicError('')
    }

    const supportsPurposeFilter =
      selectedTopics.length > 0 && selectedTopics.every(supportsConsentPurposeFilter)
    const effectiveFilterMode: PurposeFilterMode = supportsPurposeFilter ? filterMode : 'all'

    if (supportsPurposeFilter && effectiveFilterMode !== 'all' && selectedPurposes.length === 0) {
      setPurposesError(
        t(
          'subscriptions.dialog.purposesRequired',
          'Purposes are required when filtering by specific or all-except.',
        ),
      )
      hasError = true
    } else {
      setPurposesError('')
    }

    if (deliveryMode === 'webhook') {
      const trimmedUrl = callbackUrl.trim()
      if (!trimmedUrl) {
        setCallbackUrlError(
          t(
            'subscriptions.dialog.callbackUrlRequired',
            'Callback URL is required for webhook subscriptions.',
          ),
        )
        hasError = true
      } else if (!/^https?:\/\/.+/i.test(trimmedUrl)) {
        setCallbackUrlError(
          t(
            'subscriptions.dialog.callbackUrlInvalid',
            'Please provide a valid absolute URL (http:// or https://).',
          ),
        )
        hasError = true
      } else {
        setCallbackUrlError('')
      }
    } else {
      setCallbackUrlError('')
    }

    if (!sharedSecret.trim()) {
      setSecretError(t('subscriptions.dialog.secretRequired', 'Shared secret is required.'))
      hasError = true
    } else {
      setSecretError('')
    }

    if (hasError) return

    onSubmit({
      name: trimmedName,
      topics: selectedTopics,
      filter: {
        type: effectiveFilterMode,
        purposes: effectiveFilterMode !== 'all' ? selectedPurposes : undefined,
      },
      delivery: {
        mode: deliveryMode,
        callbackUrl: deliveryMode === 'webhook' ? callbackUrl.trim() : undefined,
        sharedSecret: sharedSecret.trim(),
      },
    })
  }

  const supportsPurposeFilter =
    selectedTopics.length > 0 && selectedTopics.every(supportsConsentPurposeFilter)

  return (
    <Dialog
      open={open}
      onClose={loading || selectionBusy ? undefined : onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          maxHeight: 'calc(100dvh - 64px)',
          overflow: 'hidden',
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
          textAlign: 'center',
          flexShrink: 0,
          position: 'relative',
        }}
      >
        <IconButton
          aria-label={t('subscriptions.topicUi.close', 'Close dialog')}
          disabled={loading || selectionBusy}
          onClick={onClose}
          sx={{ position: 'absolute', top: 1, right: 1 }}
        >
          <X size={20} />
        </IconButton>
        <Stack spacing={0.75}>
          <Typography
            variant="h5"
            sx={{
              fontWeight: 700,
              fontSize: { xs: '1.25rem', sm: '1.375rem' },
            }}
          >
            {t('subscriptions.dialog.registerTitle', 'Create Subscription')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              'subscriptions.dialog.registerSubtitle',
              'Create and configure a new event notification subscription and delivery mode',
            )}
          </Typography>
        </Stack>
      </DialogTitle>

      <Box
        component="form"
        onSubmit={handleSubmit}
        noValidate
        sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}
      >
        <DialogContent sx={{ px: 3, py: 3, flex: 1, minHeight: 0, overflowY: 'auto' }}>
          <Stack spacing={2.5}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            {topicError ? <Alert severity="error">{topicError}</Alert> : null}
            {purposesError ? <Alert severity="error">{purposesError}</Alert> : null}

            {/* Subscription Name */}
            <Box>
              <InputLabel
                htmlFor="subscription-name-input"
                shrink={false}
                sx={{
                  position: 'static',
                  transform: 'none',
                  mb: 0.75,
                  fontWeight: 500,
                  fontSize: '0.875rem',
                  color: 'text.primary',
                  '&.Mui-focused': { color: 'text.primary' },
                }}
              >
                {t('subscriptions.dialog.nameLabel', 'Subscription Name')}
                <Box component="span" sx={{ color: 'error.main', ml: 0.5 }}>
                  *
                </Box>
              </InputLabel>
              <TextField
                id="subscription-name-input"
                required
                fullWidth
                size="small"
                placeholder={t(
                  'subscriptions.dialog.namePlaceholder',
                  'e.g. Orders Notification Webhook',
                )}
                value={name}
                error={Boolean(nameError)}
                helperText={nameError}
                inputProps={{
                  maxLength: 225,
                  'aria-label': t('subscriptions.dialog.nameLabel', 'Subscription Name'),
                }}
                onChange={(e) => {
                  setName(e.target.value)
                  if (nameError) setNameError('')
                }}
              />
            </Box>

            {/* Topics Picker with Category */}
            <SubscriptionTopicPicker
              selected={selectedTopics}
              onBusyChange={setSelectionBusy}
              onChange={(values) => {
                setSelectedTopics(values)
                if (!values.every(supportsConsentPurposeFilter)) {
                  setFilterMode('all')
                  setSelectedPurposes([])
                  setPurposesError('')
                }
                setTopicError('')
              }}
            />

            {/* Consent Purpose Filter Mode (only for consent topics) */}
            {supportsPurposeFilter ? (
              <>
                <FormControl fullWidth size="small">
                  <InputLabel
                    id="filter-mode-label"
                    shrink={false}
                    sx={{
                      position: 'static',
                      transform: 'none',
                      mb: 0.75,
                      fontWeight: 500,
                      fontSize: '0.875rem',
                      color: 'text.primary',
                      '&.Mui-focused': { color: 'text.primary' },
                    }}
                  >
                    {t('subscriptions.dialog.filterModeLabel', 'Consent Purpose Filter Mode')}
                  </InputLabel>
                  <Select
                    labelId="filter-mode-label"
                    id="filter-mode-select"
                    value={filterMode}
                    onChange={(e) => setFilterMode(e.target.value as PurposeFilterMode)}
                  >
                    {PURPOSE_FILTER_MODES.map((mode) => (
                      <MenuItem key={mode} value={mode}>
                        {t(
                          `subscriptions.filterType.${mode}`,
                          mode === 'all'
                            ? 'All Purposes'
                            : mode === 'specific'
                              ? 'Specific Purposes'
                              : 'All Except Purposes',
                        )}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                {filterMode !== 'all' ? (
                  <SubscriptionPurposePicker
                    selected={selectedPurposes}
                    disabled={loading}
                    error={purposesError}
                    onChange={(values) => {
                      setSelectedPurposes(values)
                      if (purposesError) setPurposesError('')
                    }}
                  />
                ) : null}
              </>
            ) : null}

            {/* Delivery Mode */}
            <FormControl fullWidth size="small">
              <InputLabel
                id="delivery-mode-label"
                shrink={false}
                sx={{
                  position: 'static',
                  transform: 'none',
                  mb: 0.75,
                  fontWeight: 500,
                  fontSize: '0.875rem',
                  color: 'text.primary',
                  '&.Mui-focused': { color: 'text.primary' },
                }}
              >
                {t('subscriptions.dialog.deliveryModeLabel', 'Delivery Mode')}
              </InputLabel>
              <Select
                labelId="delivery-mode-label"
                id="delivery-mode-select"
                value={deliveryMode}
                onChange={(e) => setDeliveryMode(e.target.value as DeliveryMode)}
              >
                {DELIVERY_MODES.map((mode) => (
                  <MenuItem key={mode} value={mode}>
                    {t(
                      `subscriptions.deliveryMode.${mode}`,
                      mode === 'webhook' ? 'Webhook' : 'Poll',
                    )}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {/* Webhook Callback URL */}
            {deliveryMode === 'webhook' ? (
              <Box>
                <InputLabel
                  htmlFor="subscription-callback-url-input"
                  shrink={false}
                  sx={{
                    position: 'static',
                    transform: 'none',
                    mb: 0.75,
                    fontWeight: 500,
                    fontSize: '0.875rem',
                    color: 'text.primary',
                    '&.Mui-focused': { color: 'text.primary' },
                  }}
                >
                  {t('subscriptions.dialog.callbackUrlLabel', 'Webhook Callback URL')}
                  <Box component="span" sx={{ color: 'error.main', ml: 0.5 }}>
                    *
                  </Box>
                </InputLabel>
                <TextField
                  id="subscription-callback-url-input"
                  required
                  fullWidth
                  size="small"
                  placeholder="https://example.com/webhook"
                  value={callbackUrl}
                  error={Boolean(callbackUrlError)}
                  helperText={callbackUrlError}
                  inputProps={{
                    'aria-label': t(
                      'subscriptions.dialog.callbackUrlLabel',
                      'Webhook Callback URL',
                    ),
                  }}
                  onChange={(e) => {
                    setCallbackUrl(e.target.value)
                    if (callbackUrlError) setCallbackUrlError('')
                  }}
                />
              </Box>
            ) : null}

            {/* Shared Secret */}
            <Box>
              <InputLabel
                htmlFor="subscription-shared-secret-input"
                shrink={false}
                sx={{
                  position: 'static',
                  transform: 'none',
                  mb: 0.75,
                  fontWeight: 500,
                  fontSize: '0.875rem',
                  color: 'text.primary',
                  '&.Mui-focused': { color: 'text.primary' },
                }}
              >
                {t('subscriptions.dialog.secretLabel', 'Shared Secret')}
                <Box component="span" sx={{ color: 'error.main', ml: 0.5 }}>
                  *
                </Box>
              </InputLabel>
              <TextField
                id="subscription-shared-secret-input"
                required
                fullWidth
                size="small"
                value={sharedSecret}
                error={Boolean(secretError)}
                helperText={
                  secretError ||
                  t(
                    'subscriptions.dialog.secretHelper',
                    'Used to sign webhook payloads or authenticate poll requests',
                  )
                }
                inputProps={{
                  'aria-label': t('subscriptions.dialog.secretLabel', 'Shared Secret'),
                  style: { fontFamily: 'monospace', fontSize: '0.85rem' },
                }}
                onChange={(e) => {
                  setSharedSecret(e.target.value)
                  if (secretError) setSecretError('')
                }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Key size={16} />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        size="small"
                        title={t('subscriptions.dialog.generateSecret', 'Generate new secret')}
                        aria-label={t('subscriptions.dialog.generateSecret', 'Generate new secret')}
                        onClick={handleGenerateSecret}
                        edge="end"
                      >
                        <RefreshCw size={16} />
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
              />
            </Box>
          </Stack>
        </DialogContent>

        <DialogActions
          sx={{
            px: 3,
            py: 2,
            borderTop: 1,
            borderColor: 'divider',
            bgcolor: 'background.default',
            justifyContent: 'space-between',
            flexShrink: 0,
            gap: 2,
          }}
        >
          <Button variant="outlined" disabled={selectionBusy} onClick={onClose}>
            {t('consentRegistry.modals.actions.cancel', 'Cancel')}
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={loading || selectionBusy || selectedTopics.length === 0}
          >
            {loading && <CircularProgress size={16} color="inherit" sx={{ mr: 1 }} />}
            {!loading && t('subscriptions.dialog.registerSubmit', 'Create Subscription')}
            {loading && t('subscriptions.dialog.registering', 'Creating...')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}

SubscriptionRegisterDialog.defaultProps = { error: undefined }
