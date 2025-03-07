# elsapiens-background-location

Save background location in sql lite and make available to cpacitor subscription when app is active

## Install

```bash
npm install elsapiens-background-location
npx cap sync
```

## API

<docgen-index>

* [`startTracking(...)`](#starttracking)
* [`stopTracking()`](#stoptracking)
* [`getStoredLocations(...)`](#getstoredlocations)
* [`clearStoredLocations()`](#clearstoredlocations)
* [`addListener('locationUpdate', ...)`](#addlistenerlocationupdate-)
* [`addListener('locationStatus', ...)`](#addlistenerlocationstatus-)
* [`getLastLocation(...)`](#getlastlocation)
* [`startLocationStatusTracking()`](#startlocationstatustracking)
* [`stopLocationStatusTracking()`](#stoplocationstatustracking)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startTracking(...)

```typescript
startTracking({ reference, highAccuracy, minDistance, interval }: { reference: string; highAccuracy: boolean; minDistance: number; interval: number; }) => Promise<void>
```

| Param     | Type                                                                                              |
| --------- | ------------------------------------------------------------------------------------------------- |
| **`__0`** | <code>{ reference: string; highAccuracy: boolean; minDistance: number; interval: number; }</code> |

--------------------


### stopTracking()

```typescript
stopTracking() => Promise<void>
```

--------------------


### getStoredLocations(...)

```typescript
getStoredLocations({ reference }: { reference: string; }) => Promise<{ locations: LocationData[]; }>
```

| Param     | Type                                |
| --------- | ----------------------------------- |
| **`__0`** | <code>{ reference: string; }</code> |

**Returns:** <code>Promise&lt;{ locations: LocationData[]; }&gt;</code>

--------------------


### clearStoredLocations()

```typescript
clearStoredLocations() => Promise<void>
```

--------------------


### addListener('locationUpdate', ...)

```typescript
addListener(eventName: 'locationUpdate', listenerFunc: (data: LocationData) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                     |
| ------------------ | ------------------------------------------------------------------------ |
| **`eventName`**    | <code>'locationUpdate'</code>                                            |
| **`listenerFunc`** | <code>(data: <a href="#locationdata">LocationData</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('locationStatus', ...)

```typescript
addListener(eventName: 'locationStatus', listenerFunc: (status: { enabled: boolean; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                    |
| ------------------ | ------------------------------------------------------- |
| **`eventName`**    | <code>'locationStatus'</code>                           |
| **`listenerFunc`** | <code>(status: { enabled: boolean; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### getLastLocation(...)

```typescript
getLastLocation({ reference }: { reference: string; }) => Promise<void>
```

| Param     | Type                                |
| --------- | ----------------------------------- |
| **`__0`** | <code>{ reference: string; }</code> |

--------------------


### startLocationStatusTracking()

```typescript
startLocationStatusTracking() => Promise<void>
```

--------------------


### stopLocationStatusTracking()

```typescript
stopLocationStatusTracking() => Promise<void>
```

--------------------


### Interfaces


#### LocationData

| Prop                   | Type                |
| ---------------------- | ------------------- |
| **`reference`**        | <code>string</code> |
| **`index`**            | <code>number</code> |
| **`latitude`**         | <code>number</code> |
| **`longitude`**        | <code>number</code> |
| **`altitude`**         | <code>number</code> |
| **`speed`**            | <code>number</code> |
| **`heading`**          | <code>number</code> |
| **`accuracy`**         | <code>number</code> |
| **`altitudeAccuracy`** | <code>number</code> |
| **`totalDistance`**    | <code>number</code> |
| **`timestamp`**        | <code>number</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

</docgen-api>
