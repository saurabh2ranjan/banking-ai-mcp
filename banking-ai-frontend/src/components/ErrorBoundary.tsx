import { Component, ErrorInfo, ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  message?: string;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error("UI error", error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="app-shell">
          <main className="main">
            <section className="content">
              <div className="card">
                <div className="card-header">
                  <div>
                    <div className="card-title">Something went wrong</div>
                    <div className="card-subtitle">
                      The UI encountered an error while rendering.
                    </div>
                  </div>
                </div>
                <div className="error-text">
                  {this.state.message ?? "Unknown error"}
                </div>
              </div>
            </section>
          </main>
        </div>
      );
    }
    return this.props.children;
  }
}

