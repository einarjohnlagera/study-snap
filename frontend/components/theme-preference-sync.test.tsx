import { render, waitFor } from "@testing-library/react";
import { ThemePreferenceSync } from "./theme-preference-sync";
import { getAuthUser } from "@/lib/auth";

const setThemeMock = jest.fn();

jest.mock("next-themes", () => ({
  useTheme: () => ({
    setTheme: setThemeMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("ThemePreferenceSync", () => {
  beforeEach(() => {
    setThemeMock.mockReset();
    (getAuthUser as jest.Mock).mockReset();
  });

  it("syncs the stored authenticated theme preference", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      themePreference: "DARK",
    });

    render(<ThemePreferenceSync />);

    await waitFor(() => {
      expect(setThemeMock).toHaveBeenCalledWith("dark");
    });
  });

  it("falls back to system for authenticated users without a stored theme", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      themePreference: null,
    });

    render(<ThemePreferenceSync />);

    await waitFor(() => {
      expect(setThemeMock).toHaveBeenCalledWith("system");
    });
  });
});
