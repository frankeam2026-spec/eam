import { request } from '../../api';
import {
  inventoryApi,
  receiptApi,
  requisitionApi,
  inventoryQueryApi,
  operationApi,
} from '../../inventory';
import { usePagedRows, type QueryPage } from '../usePagedRows';

export const useInventoryMasterList = () => usePagedRows(inventoryApi.list);
export const useReceiptList = () => usePagedRows(receiptApi.list);
export const useRequisitionList = () => usePagedRows(requisitionApi.list);
export const useInventoryQueryList = () => usePagedRows(inventoryQueryApi.list);
export const useInventoryOperationList = () => usePagedRows(operationApi.list);
export function useOutboundList<T>() {
  return usePagedRows(
    async (pending: boolean, q: string, conditions: string, page: number, size: number) => {
      const endpoint = pending ? '/inventory/outbounds/pending' : '/inventory/outbounds';
      const parameters = new URLSearchParams({
        q,
        conditions,
        page: String(page),
        size: String(size),
      });
      return request<QueryPage<T>>(`${endpoint}?${parameters}`);
    },
  );
}
