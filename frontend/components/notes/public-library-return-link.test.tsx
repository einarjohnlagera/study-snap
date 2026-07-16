import { fireEvent, render, screen } from "@testing-library/react";
import { PublicLibraryReturnLink } from "./public-library-return-link";
import { PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY } from "@/lib/public-library-url";

describe("PublicLibraryReturnLink", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it("renders a real link to the given href", () => {
    render(
      <PublicLibraryReturnLink href="/public/library/nursing/vital-signs" returnUrl="/public/library/nursing">
        Vital Signs
      </PublicLibraryReturnLink>,
    );

    expect(screen.getByRole("link", { name: "Vital Signs" })).toHaveAttribute(
      "href",
      "/public/library/nursing/vital-signs",
    );
  });

  it("saves the return URL to sessionStorage on click, before navigating", () => {
    render(
      <PublicLibraryReturnLink href="/public/library/nursing/vital-signs" returnUrl="/public/library/nursing">
        Vital Signs
      </PublicLibraryReturnLink>,
    );

    expect(window.sessionStorage.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY)).toBeNull();

    fireEvent.click(screen.getByRole("link", { name: "Vital Signs" }));

    expect(window.sessionStorage.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY)).toBe(
      "/public/library/nursing",
    );
  });
});
