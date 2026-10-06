// A developer .env must never cause React development code in the deploy bundle.
process.env.NODE_ENV='production';
const {build}=await import('vite');
await build();
