import { registerPlugin } from '@capacitor/core';

import type { BackgroudLocationPlugin } from './definitions';

const BackgroudLocation = registerPlugin<BackgroudLocationPlugin>('BackgroudLocation', {
  web: () => import('./web').then((m) => new m.BackgroudLocationWeb()),
});

export * from './definitions';
export { BackgroudLocation };
