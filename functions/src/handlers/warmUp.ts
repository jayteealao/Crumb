import { onRequest } from "firebase-functions/v2/https";

export const warmUp = onRequest((_req, res) => {
  res.status(200).send("ok");
});
