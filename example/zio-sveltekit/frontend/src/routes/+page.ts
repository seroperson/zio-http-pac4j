export const prerender = true;

import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
  const res = await fetch(`/api/profile`);
  if (res.status == 200) {
    return {
      profile: await res.json()
    };
  } else {
    return {
      profile: undefined
    };
  }
};
