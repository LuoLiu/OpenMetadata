/*
 *  Copyright 2024 Collate.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { TableProfilerTab } from '../components/Database/Profiler/ProfilerDashboard/profilerDashboard.interface';
import { getEntityDetailsPath } from '../constants/constants';
import { EntityTabs, EntityType } from '../enums/entity.enum';
import { getTestSuitePath } from './RouterUtils';

export const getTestSuiteDetailsPath = ({
  isExecutableTestSuite,
  fullyQualifiedName,
}: {
  fullyQualifiedName: string;
  isExecutableTestSuite?: boolean;
}) =>
  isExecutableTestSuite
    ? `${getEntityDetailsPath(
        EntityType.TABLE,
        fullyQualifiedName,
        EntityTabs.PROFILER
      )}?activeTab=${TableProfilerTab.DATA_QUALITY}`
    : getTestSuitePath(fullyQualifiedName);

export const getTestSuiteFQN = (fqn: string) => {
  const fqnPart = fqn.split('.');
  if (fqnPart.length > 1) {
    fqnPart.pop();
  }

  return fqnPart.join('.');
};
