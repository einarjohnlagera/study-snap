import { fireEvent, render, screen } from "@testing-library/react";
import { SuggestionCombobox } from "./suggestion-combobox";
import { waitFor } from "@testing-library/react";

describe("SuggestionCombobox", () => {
  it("supports custom values when custom input is allowed", () => {
    const onChange = jest.fn();

    render(
      <SuggestionCombobox
        id="course-program"
        value=""
        options={[
          { value: "Nursing", label: "Nursing" },
          { value: "Pharmacy", label: "Pharmacy" },
        ]}
        onChange={onChange}
        allowCustom
        helperText="Pick a suggestion or type your own."
      />,
    );

    const input = screen.getByRole("textbox");
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "Marine Biology" } });

    expect(onChange).toHaveBeenLastCalledWith("Marine Biology");
    expect(screen.getByRole("button", { name: /Use "Marine Biology"/i })).toBeInTheDocument();
  });

  it("shows labels for fixed options and returns the option value on selection", () => {
    const onChange = jest.fn();

    render(
      <SuggestionCombobox
        id="learner-level"
        value="COLLEGE"
        options={[
          { value: "COLLEGE", label: "College" },
          { value: "BOARD_EXAM_REVIEW", label: "Board Exam Review" },
        ]}
        onChange={onChange}
        allowCustom={false}
        helperText="Choose the option that best matches your current study stage."
      />,
    );

    const input = screen.getByRole("textbox");
    expect(input).toHaveValue("College");

    fireEvent.focus(input);
    fireEvent.click(screen.getByRole("option", { name: "Board Exam Review" }));

    expect(onChange).toHaveBeenLastCalledWith("BOARD_EXAM_REVIEW");
  });

  it("reverts to the last valid fixed option when the user types an invalid value", async () => {
    const onChange = jest.fn();

    render(
      <SuggestionCombobox
        id="learner-level"
        value="COLLEGE"
        options={[
          { value: "COLLEGE", label: "College" },
          { value: "BOARD_EXAM_REVIEW", label: "Board Exam Review" },
        ]}
        onChange={onChange}
        allowCustom={false}
        helperText="Choose the option that best matches your current study stage."
      />,
    );

    const input = screen.getByRole("textbox");
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "Graduate School" } });

    expect(onChange).not.toHaveBeenCalled();
    expect(input).toHaveValue("Graduate School");

    fireEvent.mouseDown(document.body);

    await waitFor(() => {
      expect(input).toHaveValue("College");
    });
  });

  it("filters suggestions by exact, prefix, then contains matches without showing the full list", () => {
    render(
      <SuggestionCombobox
        id="course-program"
        value=""
        options={[
          { value: "Mechanical Engineering", label: "Mechanical Engineering" },
          { value: "Engineering", label: "Engineering" },
          { value: "Software Engineering", label: "Software Engineering" },
          { value: "Civil Engineering", label: "Civil Engineering" },
          { value: "Accountancy", label: "Accountancy" },
        ]}
        onChange={jest.fn()}
        allowCustom
      />,
    );

    const input = screen.getByRole("textbox");
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "engin" } });

    expect(screen.queryByRole("option", { name: "Accountancy" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Engineering",
      "Mechanical Engineering",
      "Software Engineering",
      "Civil Engineering",
    ]);
    expect(screen.getByRole("button", { name: /Use "engin"/i })).toBeInTheDocument();
  });

  it("reuses the existing canonical option value when the user types an exact custom match with different casing", () => {
    const onChange = jest.fn();

    render(
      <SuggestionCombobox
        id="course-program"
        value=""
        options={[
          { value: "Engineering", label: "Engineering" },
          { value: "Nursing", label: "Nursing" },
        ]}
        onChange={onChange}
        allowCustom
      />,
    );

    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: " engineering " },
    });

    expect(onChange).toHaveBeenLastCalledWith("Engineering");
    expect(screen.queryByRole("button", { name: /Use "engineering"/i })).not.toBeInTheDocument();
  });
});
