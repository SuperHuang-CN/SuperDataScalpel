import { requestJson } from '../../../shared/api/http';
import type { SdkApiDocumentation } from '../model/sdkApi';

export const getSdkApiDocumentation = () => requestJson<SdkApiDocumentation>('/v1/spark-jar-sdk-api');
