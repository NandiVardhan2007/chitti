/** An error that maps directly to a JSON response: `{ "error": code, "message": message }`. */
export class HttpError extends Error {
  constructor(status, code, message) {
    super(message ?? code);
    this.status = status;
    this.code = code;
  }
}
