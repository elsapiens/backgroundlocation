export interface BackgroudLocationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
