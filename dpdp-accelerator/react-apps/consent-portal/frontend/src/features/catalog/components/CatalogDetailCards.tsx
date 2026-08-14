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

import { Box, Card, CardContent, CardHeader, Divider, Stack, Typography } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'

interface DetailItem {
  label: string
  value: React.ReactNode
}

interface OverviewCardProps {
  title: string
  items: DetailItem[]
}

export function OverviewCard({ title, items }: OverviewCardProps): React.JSX.Element {
  return (
    <Card variant="outlined">
      <CardHeader title={<Typography fontWeight={600}>{title}</Typography>} />
      <Divider />
      <CardContent>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
            gap: 3,
          }}
        >
          {items.map((item) => (
            <Stack key={item.label} spacing={0.5}>
              <Typography variant="caption" color="text.secondary">
                {item.label}
              </Typography>
              <Typography component="div" variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                {item.value || '-'}
              </Typography>
            </Stack>
          ))}
        </Box>
      </CardContent>
    </Card>
  )
}

interface PropertiesCardProps {
  properties: Record<string, string> | undefined
  schema: string | undefined
}

export function PropertiesCard({
  properties,
  schema,
}: PropertiesCardProps): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const entries = Object.entries(properties ?? {})

  if (!schema && entries.length === 0) {
    return null
  }

  return (
    <Card variant="outlined">
      <CardHeader
        title={<Typography fontWeight={600}>{t('catalog.details.definition')}</Typography>}
      />
      <Divider />
      <CardContent>
        <Stack spacing={2.5}>
          {schema ? (
            <Stack spacing={0.5}>
              <Typography variant="caption" color="text.secondary">
                {t('catalog.fields.schema')}
              </Typography>
              <Box
                component="pre"
                sx={{
                  m: 0,
                  p: 2,
                  borderRadius: 1,
                  bgcolor: 'action.hover',
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                  overflowWrap: 'anywhere',
                }}
              >
                {schema}
              </Box>
            </Stack>
          ) : null}
          {entries.length > 0 ? (
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                gap: 2,
              }}
            >
              {entries.map(([key, value]) => (
                <Stack key={key} spacing={0.5}>
                  <Typography variant="caption" color="text.secondary">
                    {key}
                  </Typography>
                  <Typography variant="body2">{value}</Typography>
                </Stack>
              ))}
            </Box>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  )
}
