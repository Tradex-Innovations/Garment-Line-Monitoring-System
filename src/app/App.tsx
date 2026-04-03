import { RouterProvider } from "react-router";
import { AuthProvider } from "./auth";
import { OperationsProvider } from "./operations-context";
import { router } from "./routes";

export default function App() {
  return (
    <AuthProvider>
      <OperationsProvider>
        <RouterProvider router={router} />
      </OperationsProvider>
    </AuthProvider>
  );
}
