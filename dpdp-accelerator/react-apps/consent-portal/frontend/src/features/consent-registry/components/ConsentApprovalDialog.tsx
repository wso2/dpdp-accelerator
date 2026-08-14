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

import { useMemo, useState } from 'react'
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Switch,
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import { useCatalogText } from '../../../i18n/catalogText'
import type { ConsentApprovalSelection, ConsentPurposeItem } from '../../../types/consent'
import { dialogContentPadding } from '../../../components/common/dialogContentPadding'

interface ConsentApprovalDialogProps {
  open: boolean
  consentId: string
  purposes: ConsentPurposeItem[]
  loading: boolean
  onClose: () => void
  onConfirm: (selectedOptionalElements: ConsentApprovalSelection[]) => void
}

function toElementKey(
  purposeId: string,
  purposeVersion: string,
  elementId: string,
  elementVersion: string,
): string {
  return `${purposeId}::${purposeVersion}::${elementId}::${elementVersion}`
}

function ConsentApprovalDialog({
  open,
  consentId,
  purposes,
  loading,
  onClose,
  onConfirm,
}: ConsentApprovalDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  // Administrator-authored wording, so it comes from resources/<lang>/catalog.ts
  // and falls back to the English the server returns.
  const catalogText = useCatalogText()

  const mandatoryElements = useMemo(
    () =>
      purposes.flatMap((purpose) =>
        purpose.elements
          .filter((element) => element.mandatory)
          .map((element) => ({
            purposeId: purpose.purposeId,
            purposeVersion: purpose.version,
            purposeLabel: catalogText('purposes', purpose).displayName,
            elementId: element.elementId,
            elementVersion: element.version,
            elementLabel: catalogText('elements', element).displayName,
          })),
      ),
    [purposes, catalogText],
  )

  const optionalElements = useMemo(
    () =>
      purposes.flatMap((purpose) =>
        purpose.elements
          .filter((element) => !element.mandatory)
          .map((element) => ({
            purposeId: purpose.purposeId,
            purposeVersion: purpose.version,
            purposeLabel: catalogText('purposes', purpose).displayName,
            elementId: element.elementId,
            elementVersion: element.version,
            elementLabel: catalogText('elements', element).displayName,
            approved: element.approved,
          })),
      ),
    [purposes, catalogText],
  )

  const initialOptionalKeys = useMemo(
    () =>
      optionalElements
        .filter((element) => element.approved)
        .map((element) =>
          toElementKey(
            element.purposeId,
            element.purposeVersion,
            element.elementId,
            element.elementVersion,
          ),
        ),
    [optionalElements],
  )

  const [selectedOptionalKeys, setSelectedOptionalKeys] = useState<string[]>(initialOptionalKeys)

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
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
        <Stack spacing={1}>
          <Typography variant="h4" fontWeight={700}>
            {t('consentRegistry.modals.approval.title', 'Review & Approve Consent')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              'consentRegistry.modals.approval.subtitle',
              'Please review the consent elements before approval.',
            )}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 300 }}>
            {t('consentRegistry.modals.consentId', 'Consent ID')}: {consentId}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={dialogContentPadding(3)}>
        {loading ? (
          <Typography variant="body2" color="text.secondary">
            {t('consentRegistry.modals.approval.loading', 'Loading consent details...')}
          </Typography>
        ) : (
          <Stack spacing={3} sx={{ mt: 0.5 }}>
            <Box>
              <Typography
                variant="caption"
                sx={{
                  display: 'block',
                  mb: 1.5,
                  fontWeight: 700,
                  color: 'primary.main',
                  letterSpacing: 0.6,
                  textTransform: 'uppercase',
                }}
              >
                {t('consentRegistry.modals.approval.mandatory', 'Mandatory Elements (Required)')}
              </Typography>
              <Stack spacing={1.25}>
                {mandatoryElements.map((element) => (
                  <Stack
                    key={toElementKey(
                      element.purposeId,
                      element.purposeVersion,
                      element.elementId,
                      element.elementVersion,
                    )}
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{
                      p: 1.5,
                      borderRadius: 1,
                      border: 1,
                      borderColor: 'divider',
                      bgcolor: 'background.paper',
                      gap: 1.5,
                    }}
                  >
                    <Box>
                      <Typography variant="body2" fontWeight={600}>
                        {element.elementLabel}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {element.purposeLabel}
                      </Typography>
                    </Box>
                    <Chip
                      size="small"
                      color="error"
                      variant="outlined"
                      label={t('consentRegistry.modals.approval.required', 'Required')}
                    />
                  </Stack>
                ))}

                {mandatoryElements.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    {t(
                      'consentRegistry.modals.approval.noMandatory',
                      'No mandatory requirements for this consent.',
                    )}
                  </Typography>
                ) : null}
              </Stack>
            </Box>

            {optionalElements.length > 0 ? (
              <>
                <Divider />

                <Box>
                  <Typography
                    variant="caption"
                    sx={{
                      display: 'block',
                      mb: 1.5,
                      fontWeight: 700,
                      color: 'primary.main',
                      letterSpacing: 0.6,
                      textTransform: 'uppercase',
                    }}
                  >
                    {t('consentRegistry.modals.approval.optional', 'Optional Elements')}
                  </Typography>
                  <Stack spacing={1.25}>
                    {optionalElements.map((element) => {
                      const key = toElementKey(
                        element.purposeId,
                        element.purposeVersion,
                        element.elementId,
                        element.elementVersion,
                      )
                      const checked = selectedOptionalKeys.includes(key)

                      return (
                        <Stack
                          key={key}
                          direction="row"
                          alignItems="center"
                          justifyContent="space-between"
                          sx={{
                            p: 1.5,
                            borderRadius: 1,
                            border: 1,
                            borderColor: checked ? 'primary.main' : 'divider',
                            bgcolor: 'background.paper',
                            gap: 1.5,
                          }}
                        >
                          <Box>
                            <Typography variant="body2" fontWeight={600}>
                              {element.elementLabel}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {element.purposeLabel}
                            </Typography>
                          </Box>
                          <Switch
                            checked={checked}
                            onChange={() => {
                              setSelectedOptionalKeys((previousKeys) => {
                                if (previousKeys.includes(key)) {
                                  return previousKeys.filter((existingKey) => existingKey !== key)
                                }

                                return [...previousKeys, key]
                              })
                            }}
                            slotProps={{
                              input: {
                                'aria-label': t(
                                  'consentRegistry.modals.approval.toggleWithDetails',
                                  'Toggle permission for {{elementLabel}} in {{purposeLabel}}',
                                  {
                                    elementLabel: element.elementLabel,
                                    purposeLabel: element.purposeLabel,
                                  },
                                ),
                              },
                            }}
                          />
                        </Stack>
                      )
                    })}
                  </Stack>
                </Box>
              </>
            ) : null}
          </Stack>
        )}
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
          {t('consentRegistry.modals.actions.cancel', 'Cancel')}
        </Button>
        <Button
          autoFocus
          fullWidth
          variant="contained"
          disabled={loading}
          onClick={() => {
            const selectedOptionalElements: ConsentApprovalSelection[] = optionalElements
              .filter((element) =>
                selectedOptionalKeys.includes(
                  toElementKey(
                    element.purposeId,
                    element.purposeVersion,
                    element.elementId,
                    element.elementVersion,
                  ),
                ),
              )
              .map((element) => ({
                purposeId: element.purposeId,
                purposeVersion: element.purposeVersion,
                elementId: element.elementId,
                elementVersion: element.elementVersion,
              }))

            onConfirm(selectedOptionalElements)
          }}
        >
          {loading
            ? t('consentRegistry.modals.actions.processing', 'Processing...')
            : t('consentRegistry.modals.approval.confirm', 'Approve & Continue')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ConsentApprovalDialog
