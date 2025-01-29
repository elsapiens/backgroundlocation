import { WebPlugin } from '@capacitor/core';

import type { BackgroudLocationPlugin } from './definitions';

export class BackgroudLocationWeb extends WebPlugin implements BackgroudLocationPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
