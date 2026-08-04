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
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Divider,
  Skeleton,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import {
  AlignLeft,
  Building2,
  Fingerprint,
  Tag,
  Type as TypeIcon,
} from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import CopyableText from '../../components/CopyableText'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import DetailGrid from './components/DetailGrid'
import { useElementQuery } from './hooks/useCatalogQueries'

function ElementDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const detailQuery = useElementQuery(id)
  const detail = detailQuery.data

  if (detailQuery.isLoading) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <HeaderBreadcrumbs />
          <Skeleton width={300} height={48} />
          <Skeleton variant="rounded" height={220} />
        </Stack>
      </Box>
    )
  }

  if (!id || detailQuery.isError || !detail) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={2}>
          <Typography color="error.main">{t('catalog.elements.loadFailed')}</Typography>
          <Box>
            <Button variant="outlined" onClick={() => navigate('/elements')}>
              {t('catalog.elements.back')}
            </Button>
          </Box>
        </Stack>
      </Box>
    )
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1} minWidth={0}>
          <HeaderBreadcrumbs currentLabel={detail.name} />
          <Typography variant="h4" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>
            {detail.displayName ?? detail.name}
          </Typography>
        </Stack>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <Fingerprint size={15} />
                <Typography variant="body2" fontWeight={400}>
                  {t('catalog.fields.elementId')}:
                </Typography>
                <CopyableText
                  value={detail.id}
                  monospace
                  textAriaLabel={t('copyableText.valueAriaLabel', {
                    label: t('catalog.fields.elementId'),
                    value: detail.id,
                  })}
                  copyTooltip={t('copyableText.copyLabel', {
                    label: t('catalog.fields.elementId'),
                  })}
                  copyAriaLabel={t('copyableText.copyValue', {
                    label: t('catalog.fields.elementId'),
                    value: detail.id,
                  })}
                />
              </Stack>
            }
            sx={{ pb: 1 }}
          />
          <Divider />
          <CardContent sx={{ pt: 3 }}>
            <DetailGrid
              fields={[
                {
                  icon: <Tag size={14} />,
                  label: t('catalog.fields.name'),
                  value: <Box component="code">{detail.name}</Box>,
                },
                {
                  icon: <TypeIcon size={14} />,
                  label: t('catalog.fields.displayName'),
                  value: detail.displayName ?? '-',
                },
                {
                  icon: <Building2 size={14} />,
                  label: t('catalog.fields.tenantDomain'),
                  value: detail.tenantDomain ?? '-',
                },
                {
                  icon: <AlignLeft size={14} />,
                  label: t('catalog.fields.description'),
                  value: detail.description ?? '-',
                },
              ]}
            />
          </CardContent>
        </Card>
      </Stack>
    </Box>
  )
}

export default ElementDetailsPage
