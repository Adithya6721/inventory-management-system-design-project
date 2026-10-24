import { useEffect } from 'react';

export default function useCreateRequest(requested, onHandled, setOpen) {
  useEffect(() => {
    if (requested) {
      setOpen(true);
      onHandled?.();
    }
  }, [requested, onHandled, setOpen]);
}
