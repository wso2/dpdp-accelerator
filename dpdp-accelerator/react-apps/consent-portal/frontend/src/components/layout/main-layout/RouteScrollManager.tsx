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

import { useEffect, useRef } from 'react'
import { useLocation, useNavigationType } from 'react-router-dom'

function decodeHash(hash: string): string {
  const value = hash.slice(1)

  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function RouteScrollManager(): React.JSX.Element {
  const { pathname, hash } = useLocation()
  const navigationType = useNavigationType()
  const previousPathname = useRef(pathname)
  const markerRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const pathChanged = previousPathname.current !== pathname
    previousPathname.current = pathname

    if (hash) {
      document.getElementById(decodeHash(hash))?.scrollIntoView()
      return
    }

    if (pathChanged && navigationType !== 'POP') {
      markerRef.current?.parentElement?.scrollTo({
        top: 0,
        left: 0,
        behavior: 'auto',
      })
    }
  }, [hash, navigationType, pathname])

  return <span ref={markerRef} aria-hidden="true" hidden />
}

export default RouteScrollManager
