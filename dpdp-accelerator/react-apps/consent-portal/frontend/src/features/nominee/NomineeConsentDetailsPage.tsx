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

import { Box, Button, Stack, Typography } from '@wso2/oxygen-ui'
import { ArrowLeft } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import ConsentRevocationDialog from '../consent-registry/components/ConsentRevocationDialog'
import ConsentAuthorizationsSection from '../consent-registry/components/details/ConsentAuthorizationsSection'
import ConsentMetadataCard from '../consent-registry/components/details/ConsentMetadataCard'
import ConsentPurposesSection from '../consent-registry/components/details/ConsentPurposesSection'
import ConsentResourcesModal from '../consent-registry/components/details/ConsentResourcesModal'
import { isConsentRevokableStatus } from '../consent-registry/utils/statusChip'
import { useActingAs } from './actingAs/actingAsContext'
import {
  useRevokeActingConsentMutation,
  useActingConsentDetailQuery,
} from './hooks/useNomineeQueries'

function formatResourcesForModal(resources: unknown): string {
  if (!resources) {
    return '-'
  }
  if (typeof resources === 'string') {
    try {
      return JSON.stringify(JSON.parse(resources), null, 2)
    } catch {
      return resources
    }
  }
  try {
    return JSON.stringify(resources, null, 2)
  } catch {
    return String(resources)
  }
}

function NomineeConsentDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { ownerId, consentId } = useParams<{ ownerId: string; consentId: string }>()
  const navigate = useNavigate()
  const { session } = useActingAs()

  const resolvedOwnerId = ownerId ?? ''
  const detailQuery = useActingConsentDetailQuery(consentId, Boolean(session))
  const revokeMutation = useRevokeActingConsentMutation()

  const [revocationDialogOpen, setRevocationDialogOpen] = useState<boolean>(false)
  const [resourcesModalOpen, setResourcesModalOpen] = useState<boolean>(false)
  const [selectedResourcesJson, setSelectedResourcesJson] = useState<string>('')

  const backTo = `/nominee/manage/${resolvedOwnerId}`
  const detail = detailQuery.data
  const canRevoke = detail ? isConsentRevokableStatus(detail.status) : false

  if (!consentId || detailQuery.isError || (!detailQuery.isLoading && !detail)) {
    return (
      <Box
        component="main"
        sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        <Typography color="error.main">
          {t('nominee.manage.messages.loadFailed', 'Unable to load consents right now.')}
        </Typography>
        <Box>
          <Button variant="outlined" onClick={() => navigate(backTo)}>
            {t('nominee.detail.back', 'Back')}
          </Button>
        </Box>
      </Box>
    )
  }

  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack spacing={1}>
        <HeaderBreadcrumbs />
        <Button
          size="small"
          variant="text"
          startIcon={<ArrowLeft size={16} />}
          sx={{ alignSelf: 'flex-start' }}
          onClick={() => navigate(backTo)}
        >
          {t('nominee.detail.back', 'Back')}
        </Button>
        <Box sx={{ position: 'relative' }}>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            {t('nominee.detail.title', 'Consent Details')}
          </Typography>
          {detail ? (
            <Button
              variant="contained"
              color="error"
              size="small"
              disabled={revokeMutation.isPending || !canRevoke}
              onClick={() => {
                setRevocationDialogOpen(true)
              }}
              sx={{
                mt: { xs: 2, md: 0 },
                position: { xs: 'static', md: 'absolute' },
                right: { md: 0 },
                bottom: { md: 0 },
              }}
            >
              {t('consentRegistry.actions.revoke', 'Revoke')}
            </Button>
          ) : null}
        </Box>
      </Stack>

      {detail ? (
        <>
          <ConsentMetadataCard consentId={consentId} detail={detail} />
          <ConsentPurposesSection purposes={detail.purposes} />
          <ConsentAuthorizationsSection
            authorizations={detail.authorizations ?? []}
            onViewResources={(resources) => {
              setSelectedResourcesJson(formatResourcesForModal(resources))
              setResourcesModalOpen(true)
            }}
          />

          <ConsentResourcesModal
            open={resourcesModalOpen}
            resourcesJson={selectedResourcesJson}
            onClose={() => {
              setResourcesModalOpen(false)
            }}
          />

          <ConsentRevocationDialog
            key={`nominee-detail-revoke-${consentId}-${String(revocationDialogOpen)}`}
            open={revocationDialogOpen}
            consentId={consentId}
            loading={revokeMutation.isPending}
            onClose={() => {
              setRevocationDialogOpen(false)
            }}
            onConfirm={() => {
              revokeMutation.mutate(consentId, {
                onSuccess: () => {
                  setRevocationDialogOpen(false)
                  navigate(backTo)
                },
              })
            }}
          />
        </>
      ) : null}
    </Box>
  )
}

export default NomineeConsentDetailsPage
