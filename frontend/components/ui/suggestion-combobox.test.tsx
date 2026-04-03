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
});
