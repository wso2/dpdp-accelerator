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

import { Box, IconButton, MenuItem, Skeleton, TextField, Tooltip } from '@wso2/oxygen-ui'
import { RefreshCw } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useElementVersionsQuery } from '../hooks/useCatalogQueries'

interface ElementVersionSelectProps {
  elementId: string | undefined
  latestVersion: string | undefined
  value: string
  label: string
  allowAny: boolean
  onChange: (version: string) => void
}

function ElementVersionSelect({
  elementId,
  latestVersion,
  value,
  label,
  allowAny,
  onChange,
}: ElementVersionSelectProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const versionsQuery = useElementVersionsQuery(elementId)
  const versions = useMemo(() => {
    const versionValues = versionsQuery.data?.versions.map((version) => version.version) ?? []

    if (latestVersion && !versionValues.includes(latestVersion)) {
      return [latestVersion, ...versionValues]
    }

    return versionValues
  }, [latestVersion, versionsQuery.data?.versions])

  useEffect(() => {
    if (
      versionsQuery.isSuccess &&
      versions.length > 0 &&
      !versions.includes(value) &&
      !(allowAny && value === '')
    ) {
      onChange(latestVersion && versions.includes(latestVersion) ? latestVersion : versions[0])
    }
  }, [allowAny, latestVersion, onChange, value, versions, versionsQuery.isSuccess])

  if (!elementId) {
    return (
      <Tooltip arrow title={t('catalog.help.selectElementFirst')}>
        <Box sx={{ width: '100%' }}>
          <TextField
            select
            disabled
            fullWidth
            size="small"
            label={label}
            value="placeholder"
            slotProps={{
              select: {
                inputProps: {
                  'aria-label': t('catalog.fields.version'),
                },
              },
            }}
          >
            <MenuItem value="placeholder">—</MenuItem>
          </TextField>
        </Box>
      </Tooltip>
    )
  }

  if (versionsQuery.isPending || versionsQuery.isFetching) {
    return (
      <Skeleton variant="rounded" height={40} aria-label={t('catalog.messages.loadingVersions')} />
    )
  }

  if (versionsQuery.isError) {
    return (
      <Box sx={{ position: 'relative' }}>
        <TextField
          select
          disabled
          error
          fullWidth
          size="small"
          label={label}
          value="unavailable"
          slotProps={{
            select: {
              inputProps: {
                'aria-label': t('catalog.fields.version'),
              },
            },
          }}
        >
          <MenuItem value="unavailable">—</MenuItem>
        </TextField>
        <Tooltip arrow title={t('catalog.messages.versionsLoadFailed')}>
          <IconButton
            size="small"
            aria-label={t('catalog.actions.retry')}
            onClick={() => versionsQuery.refetch()}
            sx={{
              position: 'absolute',
              top: '50%',
              right: 26,
              zIndex: 1,
              transform: 'translateY(-50%)',
              bgcolor: 'background.paper',
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            <RefreshCw size={15} />
          </IconButton>
        </Tooltip>
      </Box>
    )
  }

  if (versions.length === 0) {
    return (
      <Tooltip arrow title={t('catalog.messages.noVersions')}>
        <Box sx={{ width: '100%' }}>
          <TextField
            select
            disabled
            fullWidth
            size="small"
            label={label}
            value="empty"
            slotProps={{
              select: {
                inputProps: {
                  'aria-label': t('catalog.fields.version'),
                },
              },
            }}
          >
            <MenuItem value="empty">—</MenuItem>
          </TextField>
        </Box>
      </Tooltip>
    )
  }

  return (
    <TextField
      select
      fullWidth
      size="small"
      label={label}
      value={value}
      slotProps={{
        inputLabel: {
          shrink: allowAny ? true : undefined,
        },
        select: {
          displayEmpty: allowAny,
          inputProps: {
            'aria-label': t('catalog.fields.version'),
          },
        },
      }}
      onChange={(event) => onChange(event.target.value)}
    >
      {allowAny ? <MenuItem value="">{t('catalog.values.any')}</MenuItem> : null}
      {versions.map((version) => (
        <MenuItem key={version} value={version}>
          {version}
        </MenuItem>
      ))}
    </TextField>
  )
}

export default ElementVersionSelect
