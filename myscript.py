#!/usr/bin/env python3
import os
import datetime
import subprocess
import random
import time

def run_git_command(cmd, env=None):
    subprocess.run(cmd, check=True, env=env)

# More realistic commit messages
COMMIT_MESSAGES = [
    "Fix typo in documentation",
    "Update README with installation instructions",
    "Add new feature for {0}",
    "Refactor {0} module for better performance",
    "Fix bug in {0} that caused incorrect output",
    "Implement {0} functionality",
    "Update dependencies",
    "Remove deprecated code",
    "Add tests for {0}",
    "Merge branch 'feature/{0}'",
    "Initial commit for {0}",
    "Optimize {0} algorithm",
    "Fix edge case in {0}",
    "Add error handling for {0}",
    "Update configuration settings"
]

# Retry/fix commit messages for high-frequency days
RETRY_MESSAGES = [
    "Fix typo in previous commit",
    "Fix build failure",
    "Another attempt to fix {0}",
    "Try different approach for {0} fix",
    "Revert previous commit",
    "Quick fix for CI pipeline",
    "Fix failing tests",
    "Address code review comments"
]

# Components to fill in the commit messages
COMPONENTS = [
    "user authentication", "data processing", "API", "dashboard", 
    "search functionality", "notification system", "cache", "database",
    "file upload", "admin panel", "reporting", "UI", "frontend",
    "backend", "analytics", "payment processing"
]

def get_commit_message(is_retry=False):
    if is_retry:
        message = random.choice(RETRY_MESSAGES)
    else:
        message = random.choice(COMMIT_MESSAGES)
    
    if "{0}" in message:
        component = random.choice(COMPONENTS)
        message = message.format(component)
    
    # Occasionally add more details to the commit message
    if random.random() < 0.2:
        details = [
            "Closes #{}".format(random.randint(1, 500)),
            "Part of the {} initiative".format(random.choice(["Q1", "Q2", "Q3", "Q4", "performance", "security"])),
            "Requested by the {} team".format(random.choice(["product", "design", "marketing", "sales"]))
        ]
        message += "\n\n" + random.choice(details)
    
    return message

def add_commit(commit_dt, file_path, is_retry=False):
    # Get a realistic commit message
    message = get_commit_message(is_retry)
    
    # ensure file exists
    open(file_path, "a").close()
    
    # append content with more variation
    with open(file_path, "a") as f:
        if is_retry:
            # For retry commits, use more specific language indicating fixes
            content_options = [
                f"// Fix attempt at {commit_dt.strftime('%H:%M:%S')}\n",
                f"// FIXME: This still doesn't work properly\n",
                f"// Try #{random.randint(2, 5)}\n",
                f"/* Debugging issue #{random.randint(1, 100)} */\n"
            ]
        else:
            content_options = [
                f"Update for {commit_dt.strftime('%Y-%m-%d')}\n",
                f"// TODO: Improve this section later\n",
                f"/* Changes made on {commit_dt.strftime('%Y-%m-%d')} */\n",
                f"// Fixed issue #{random.randint(1, 100)}\n"
            ]
        f.write(random.choice(content_options))
    
    # stage and commit (no push)
    run_git_command(["git", "add", file_path])
    date_str = commit_dt.strftime("%Y-%m-%d %H:%M:%S")
    env = os.environ.copy()
    env["GIT_AUTHOR_DATE"] = date_str
    env["GIT_COMMITTER_DATE"] = date_str
    run_git_command(
        ["git", "commit", "-m", message, "--date", date_str],
        env=env
    )

def is_vacation_period(date):
    # Increased vacation periods for sparser commits
    vacation_seed = date.year * 10000 + date.month * 100 + date.day
    random.seed(vacation_seed)
    is_vacation = random.random() < 0.05  # ~1 week in 20 is a vacation (increased from 0.02)
    random.seed(time.time())  # Reset the seed
    return is_vacation

def is_busy_period(date):
    # Reduced frequency of busy periods
    busy_seed = date.year * 10000 + date.month * 100 + date.day
    random.seed(busy_seed)
    is_busy = random.random() < 0.04  # ~2 weeks per year (reduced from 0.08)
    random.seed(time.time())  # Reset the seed
    return is_busy

def is_retry_day(date):
    # Reduced to about once every 2-3 months instead of monthly
    retry_seed = date.year * 10000 + date.month * 100
    random.seed(retry_seed)
    # Only consider the first 3 months of the year (Q1) and third quarter months (Q3)
    is_considered_month = (date.month <= 3) or (7 <= date.month <= 9)
    if not is_considered_month:
        random.seed(time.time())
        return False
        
    # For considered months, pick one random day
    retry_day = random.randint(1, 28)
    is_retry = date.day == retry_day and random.random() < 0.6  # 60% chance the selected day is a retry day
    
    random.seed(time.time())  # Reset the seed
    return is_retry

def is_active_day(date):
    # Reduced to about 1 day per month instead of 2-3
    active_seed = date.year * 10000 + date.month * 100 + date.day
    random.seed(active_seed)
    
    # Pick only 1 potential active day per month
    potential_day = random.randint(1, 28)
    is_active = date.day == potential_day and random.random() < 0.5  # 50% chance (reduced from 60%)
    
    random.seed(time.time())  # Reset the seed
    return is_active

def main():
    file_path = "my_file.txt"
    start_date = datetime.date(2021, 2, 2)
    end_date = datetime.date.today()
    one_day = datetime.timedelta(days=1)

    # Track all days that will have commits
    commit_days = {}
    
    current = start_date
    while current <= end_date:
        # Check for weeks to skip entirely (increased no-commit chance)
        if random.random() < 0.3:  # 30% chance to skip a week entirely
            current = current + datetime.timedelta(days=7)
            continue
            
        # define this week slice
        week_start = current
        week_end = min(end_date, week_start + datetime.timedelta(days=6))
        days_in_week = [
            week_start + datetime.timedelta(days=i)
            for i in range((week_end - week_start).days + 1)
        ]
        
        # Check if this is a vacation period
        if is_vacation_period(week_start):
            # Skip this week - simulate being on vacation
            current = week_end + one_day
            continue
        
        # First, identify special days in this week (retry days and active days)
        retry_days = [day for day in days_in_week if is_retry_day(day)]
        active_days = [day for day in days_in_week if is_active_day(day) and day not in retry_days]
        
        # For normal days, determine weekly commit pattern
        # Variable commit pattern - increased chance of zero commits
        chance = random.random()
        if chance < 0.40:  # 40% chance of no commits this week (increased from 20%)
            count = 0
        elif chance < 0.80:  # 40% chance of just 1 commit
            count = 1
        elif chance < 0.95:  # 15% chance of 2 commits
            count = 2
        else:  # 5% chance of 3 commits
            count = 3
        
        # If it's a busy period, increase the number of commits slightly
        if is_busy_period(week_start):
            count += random.randint(1, 2)  # Add just 1-2 more (reduced from 2-4)
            
        # For weeks with commits, decide which days to commit on (excluding special days)
        regular_days = [day for day in days_in_week if day not in retry_days and day not in active_days]
        
        if count > 0 and regular_days:
            # Allocate commits to regular days
            days_with_commits = random.sample(regular_days, min(count, len(regular_days)))
            
            for day in days_with_commits:
                # Regular days almost always get just 1 commit
                commit_days[day] = 1 if random.random() < 0.9 else 2  # 90% chance of 1 commit
        
        # Add commits for active days (moderate number: 2-5, reduced from 3-7)
        for day in active_days:
            commit_days[day] = random.randint(2, 5)
        
        # Add commits for retry days (reduced range: 4-10 from 5-12)
        for day in retry_days:
            commit_days[day] = random.randint(4, 10)
        
        # advance to next week
        current = week_end + one_day
    
    # Now make all the commits based on our plan
    for day, num_commits in sorted(commit_days.items()):
        is_retry = is_retry_day(day)
        
        # Base time for first commit of the day
        if day == end_date:
            base_dt = datetime.datetime.now()
        else:
            # Simple datetime - we're ignoring time precision as requested
            base_dt = datetime.datetime(day.year, day.month, day.day, 12, 0, 0)
        
        # Different timing patterns for different day types
        if is_retry:
            # Retry days: commits in quick succession
            for i in range(num_commits):
                commit_dt = base_dt + datetime.timedelta(minutes=i*15)  # Just a placeholder time
                add_commit(commit_dt, file_path, is_retry=True)
        elif num_commits >= 2:  
            # Active days: multiple commits
            for i in range(num_commits):
                commit_dt = base_dt + datetime.timedelta(minutes=i*60)  # Just a placeholder time
                add_commit(commit_dt, file_path, is_retry=False)
        else:
            # Regular days: just one commit
            add_commit(base_dt, file_path, is_retry=False)
    
    # Push all commits at once at the end
    run_git_command(["git", "push"])

if __name__ == "__main__":
    main()